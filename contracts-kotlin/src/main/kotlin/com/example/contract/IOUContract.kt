package com.example.contract

import com.example.state.IOUState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * An implementation of a basic smart contract in Corda
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOUState].
 *
 * For a new [IOUState] to be created, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOUState].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class IOUContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.IOUContract"
    }

    /**
     * The contract code for the [IOUContract]
     * The constraints are self documenting so don't require any additional explanation.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Create -> requireThat {
                // Generic constraints around the IOU transaction.
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)
                val out = tx.outputsOfType<IOUState>().single()
                "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
                "All of the participants must be signers." using (command.signers.containsAll(out.participants.map{ it.owningKey }))

                // IOU-specific constraints
                "The IOU's value must be non-negative." using (out.amount.quantity > 0)
            }

            is Commands.Pay -> requireThat {
                // Check there is only one group of IOUs and that there is always an input IOU
                val ious = tx.groupStates<IOUState, UniqueIdentifier> { it.linearId }.single()
                requireThat { "There must be one input IOU." } using (ious.inputs.size == 1)
                // Get input transaction
                val inputIOU = ious.inputs.single()
                // Amount paid is to lender
                "The lender and the borrower cannot have the same identity." using
                        (inputIOU.borrower != inputIOU.lender)
                // Calculate amount paid
                val amountPaid = inputIOU.amount.quantity - inputIOU.paid.quantity
                // Amount paid is positive and does not exceed payment for the IOU
                requireThat { "Amount paid is positive." using (inputIOU.paid.quantity > 0 && amountPaid >= 0)}
                // Signatures are between lender and borrower
                "Both lender and borrower together only may sign IOU transaction." using (
                        command.signers.toSet() == inputIOU.participants.map { it.owningKey }.toSet())
                // Check to see if we need an output IOU or not
                if (amountPaid > 0) {
                    // If the IOU has been fully paid then there should be no IOU output state.
                    requireThat { "There must be no output IOU as it has been fully paid." using
                            (ious.outputs.isEmpty())}
                }
                else {
                    // If the IOU has been partially paid then it should still exist
                    requireThat {"There must be one output IOU." using (ious.outputs.size == 1)}

                    // Check that only the paid property changes
                    val outputIOU = ious.outputs.single()
                    requireThat { "Only the paid amount can change." using
                            (inputIOU.copy(paid = outputIOU.paid) == outputIOU)}
                }
            }
        }
    }

    /**
     * Add any commands required for this contract as classes within this interface.
     *
     * It is useful to encapsulate your commands inside an interface, so you can use the [requireSingleCommand]
     * function to check for a number of commands which implement this interface.
     */
    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Pay : TypeOnlyCommandData(), Commands
    }
}
