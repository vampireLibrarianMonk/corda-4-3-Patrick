package com.example.test.flow

import com.example.flow.CreateFlow
import com.example.state.IOUState
import net.corda.core.contracts.Amount
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.AMOUNT
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class IOUFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode
    private lateinit var c: StartedMockNode
    private lateinit var one: Amount<Currency>
    private lateinit var zero: Amount<Currency>
    private lateinit var testAmount: Amount<Currency>

    @Before
    fun setup() {
        // Create mock network finding the contract and flow folders responsible for the flow test
        network = MockNetwork(
                MockNetworkParameters(
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.example.contract"),
                                TestCordapp.findCordapp("com.example.flow")
        )))

        // Create participants
        a = network.createPartyNode()
        b = network.createPartyNode()
        c = network.createPartyNode()

        // Create reference to repeatedly used test values
        one = AMOUNT(1, Currency.getInstance("USD"))
        zero = Amount.zero(Currency.getInstance("USD"))
        testAmount = Amount(45, Currency.getInstance("USD"))

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(a, b, c).forEach { it.registerInitiatedFlow(CreateFlow.Acceptor::class.java) }

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the initiator`() {
        val flow = CreateFlow.Initiator(one, zero, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(b.info.singleIdentity().owningKey)
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the acceptor`() {
        val flow = CreateFlow.Initiator(one, zero, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(a.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        val flow = CreateFlow.Initiator(one, zero, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(a, b)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `recorded transaction has no inputs and a single output, the input IOU`() {
        val flow = CreateFlow.Initiator(one, zero, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as IOUState
            assertEquals(recordedState.amount.quantity, 100)
            assertEquals(recordedState.lender, a.info.singleIdentity())
            assertEquals(recordedState.borrower, b.info.singleIdentity())
        }
    }

    @Test
    fun `flow records the correct IOU in both parties' vaults`() {
        val flow = CreateFlow.Initiator(one, zero, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        // We check the recorded IOU in both vaults.
        for (node in listOf(a, b)) {
            node.transaction {
                val ious = node.services.vaultService.queryBy<IOUState>().states
                assertEquals(1, ious.size)
                val recordedState = ious.single().state.data
                assertEquals(recordedState.amount.quantity, 100)
                assertEquals(recordedState.lender, a.info.singleIdentity())
                assertEquals(recordedState.borrower, b.info.singleIdentity())
            }
        }
    }


}