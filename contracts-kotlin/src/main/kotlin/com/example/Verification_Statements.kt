package com.example

const val lenderNotBorrower = "The lender and the borrower cannot be the same entity."
const val noInputsConsumed = "No inputs should be consumed when issuing an IOU."
const val lenderMustSign = "The lender must sign the contract."
const val borrowerMustSign = "The borrower must sign the contract."
const val singleStateOnly = "Only one output state should be created."
const val onlyOneInput = "There must be one input IOU."
const val lenderBorrowerOnly = "Only lender and borrower can serve as signatories."
const val noOutput = "There must be no output IOU as it has been fully paid."
const val onlyChangePayment = "Only amount paid can change on a contract."
const val mustbeIOUTransaction = "This must be an IOU transaction."