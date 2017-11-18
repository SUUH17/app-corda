package com.example.state

import com.example.contract.ItemContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.security.PublicKey
import java.util.*
/**
 * The state object recording IOU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param value the value of the IOU.
 * @param lender the party issuing the IOU.
 * @param borrower the party receiving and approving the IOU.
 */
data class ItemState(val value: String,
                     val lender: Party,
                     val borrower: Party,
                     val status: Int,
                     val time: Int,
                     val hourPrice: Int,
                     val contractId: Int,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {

    override val participants get() = listOf(lender, borrower)

    val contract get() = ItemContract()

    fun updateStatus(newStatus: Int) = copy(status = newStatus)
}
