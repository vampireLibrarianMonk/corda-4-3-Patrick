package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.IOUContract
import com.example.state.IOUState
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.workflows.asset.CashUtils
import net.corda.finance.workflows.getCashBalance
import java.lang.IllegalArgumentException
import java.util.*

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about an (partial) amount
 * paid for the IOU encapsulated within an [IOUState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
object IOUPayFlow {

    @InitiatingFlow
    @StartableByRPC
    class IOUPayFlow(val linearId: UniqueIdentifier, val amount: Amount<Currency>): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))

            val iouToSettle = serviceHub.vaultService.queryBy<IOUState>(queryCriteria).states.single()

            val counterparty = iouToSettle.state.data.lender

            if (ourIdentity != iouToSettle.state.data.borrower) {
                throw IllegalArgumentException("IOU settlement flow must be initiated by the borrower.")
            }

            val notary = iouToSettle.state.notary

            val builder = TransactionBuilder(notary = notary)

            val cashBalance = serviceHub.getCashBalance(amount.token)

            if (cashBalance < amount) {
                throw IllegalArgumentException("Borrower has only $cashBalance but needs $amount to pay.")
            }
            else if (amount > (iouToSettle.state.data.amount - iouToSettle.state.data.paid)) {
                throw IllegalArgumentException(
                        "Borrower tried to pay with $amount but only needs " +
                                "${(iouToSettle.state.data.amount - iouToSettle.state.data.paid)}")
            }

            val (_, cashKeys) = CashUtils.generateSpend(serviceHub, builder, amount, ourIdentityAndCert, counterparty)

            val settleCommand = Command(IOUContract.Commands.Pay(), listOf(counterparty.owningKey, ourIdentity.owningKey))

            builder.addCommand(settleCommand)
            builder.addInputState(iouToSettle)

            val amountRemaining = iouToSettle.state.data.amount - iouToSettle.state.data.paid - amount

            if (amountRemaining > Amount(0, amount.token)) {
                val settledIOU: IOUState = iouToSettle.state.data.pay(amount)
                builder.addOutputState(settledIOU, IOUContract.ID)
            }

            builder.verify(serviceHub)

            val myKeysToSign = (cashKeys.toSet() + ourIdentity.owningKey).toList()

            val ptx = serviceHub.signInitialTransaction(builder, myKeysToSign)

            val counterPartySession = initiateFlow(counterparty)

            subFlow(IdentitySyncFlow.Send(counterPartySession, ptx.tx))

            val stx = subFlow(CollectSignaturesFlow(ptx, listOf(counterPartySession), myOptionalKeys = myKeysToSign))

            return subFlow(FinalityFlow(stx, counterPartySession))
        }
    }
}