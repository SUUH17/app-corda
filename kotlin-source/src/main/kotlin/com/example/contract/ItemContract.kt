package com.example.contract

import com.example.state.ItemState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

open class ItemContract : Contract {
    companion object {
        @JvmStatic
        val ITEM_CONTRACT_ID = "com.example.contract.ItemContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Create -> {
                requireThat {
                    "No inputs should be consumed when issuing an Item." using (tx.inputs.isEmpty())
                    "Only one output state should be created." using (tx.outputs.size == 1)
                    val out = tx.outputsOfType<ItemState>().single()
                    "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
                    "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

                    "The type must be borrow" using (out.status == 1)
                }
            }
            is Commands.Settle -> {
                "No inputs should be consumed when issuing an Item." using (tx.inputs.size == 1)
                "Only one output state should be created." using (tx.outputs.size == 1)
                val out = tx.outputsOfType<ItemState>().single()
                "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
                "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))


                val input = tx.inputsOfType<ItemState>().single()
                "The input type must be borrowed" using (input.status == 1)
                "The type must be return" using (out.status == 2)
            }
        }
    }

    interface Commands : CommandData {
        class Create : Commands
        class Settle : Commands
    }
}
