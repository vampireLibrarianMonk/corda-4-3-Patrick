package com.example.test.contract

import com.example.*
import com.example.contract.IOUContract
import com.example.noInputsConsumed
import com.example.state.IOUState
import net.corda.core.identity.CordaX500Name
import net.corda.finance.AMOUNT
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.util.*

class IOUContractTests {
    private val ledgerServices = MockServices(listOf("com.example.contract", "com.example.flow"))
    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val iouValue = AMOUNT(1, Currency.getInstance("USD"))
    private val iouPaid = AMOUNT(0, Currency.getInstance("USD"))

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party, iouPaid))
                fails()
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party, iouPaid))
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party, iouPaid))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`(noInputsConsumed)
            }
        }
    }

    @Test
    fun `transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party, iouPaid))
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party, iouPaid))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`(singleStateOnly)
            }
        }
    }

    @Test
    fun `borrower must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party, iouPaid))
                command(miniCorp.publicKey, IOUContract.Commands.Create())
                `fails with`(borrowerMustSign)
            }
        }
    }

    @Test
    fun `lender must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, miniCorp.party, megaCorp.party, iouPaid))
                command(megaCorp.publicKey, IOUContract.Commands.Create())
                `fails with`(lenderMustSign)
            }
        }
    }

    @Test
    fun `lender is not borrower`() {
        ledgerServices.ledger {
            transaction {
                output(IOUContract.ID, IOUState(iouValue, megaCorp.party, megaCorp.party, iouPaid))
                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
                `fails with`(lenderNotBorrower)
            }
        }
    }

//    @Test // Currency by default cannot be negative
//    fun `cannot create negative-value IOUs`() {
//        ledgerServices.ledger {
//            transaction {
//                output(IOUContract.ID, IOUState(-1, iouPaid, miniCorp.party, megaCorp.party))
//                command(listOf(megaCorp.publicKey, miniCorp.publicKey), IOUContract.Commands.Create())
//                `fails with`("The IOU's value must be non-negative.")
//            }
//        }
//    }
}