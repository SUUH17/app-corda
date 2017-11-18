package com.example.flow

import net.corda.core.flows.InitiatingFlow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.ItemContract
import com.example.contract.ItemContract.Companion.ITEM_CONTRACT_ID
import com.example.state.ItemState
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class SettleFlow(val itemId: String): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val me: Party = serviceHub.myInfo.legalIdentities.first()

        val ItemId = UniqueIdentifier.fromString(itemId)

        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(ItemId))

        val itemStateId = serviceHub.vaultService.queryBy<ItemState>(queryCriteria).states.single()
        val itemState : ItemState = itemStateId.state.data
        val updatedItemState = itemState.updateStatus(newStatus = 2)

        val settleCommand = Command(ItemContract.Commands.Settle(), itemState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)

        txBuilder.addCommand(settleCommand)
        txBuilder.addOutputState(updatedItemState, ITEM_CONTRACT_ID)
        txBuilder.addInputState(itemStateId)

        txBuilder.verify(serviceHub)

        val counterpartySession =  initiateFlow(itemState.borrower)

        val ptx = serviceHub.signInitialTransaction(txBuilder)
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(counterpartySession)))
        return subFlow(FinalityFlow(stx))
    }
}

@InitiatedBy(SettleFlow::class)
class SettleAcceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an item transaction." using (output is ItemState)
            }
        }

        return subFlow(signTransactionFlow)
    }
}