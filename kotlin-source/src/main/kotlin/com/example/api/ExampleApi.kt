package com.example.api

import com.example.flow.BorrowFlow.Initiator
import com.example.flow.SettleFlow
import com.example.state.ItemState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED

val SERVICE_NAMES = listOf("Controller", "Network Map Service")

@Path("v1")
class ExampleApi(private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<ExampleApi>()
    }

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    @GET
    @Path("items")
    @Produces(MediaType.APPLICATION_JSON)
    fun getItems() = rpcOps.vaultQueryBy<ItemState>().states

    @POST
    @Path("borrow")
    @Produces(MediaType.APPLICATION_JSON)
    fun borrowItem(@QueryParam("item") itemId: Int,
                                           @QueryParam("time") time: Int,
                                           @QueryParam("hourPrice") hourPrice: Int,
                                           @QueryParam("contractId") contractId: Int,
                                           @QueryParam("partyName") partyName: CordaX500Name?): Response {
            if (itemId <= 0 ) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'item' must be non-negative.\n").build()
        }
        if (partyName == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'partyName' missing or has wrong format.\n").build()
        }
        val otherParty = rpcOps.wellKnownPartyFromX500Name(partyName) ?:
                return Response.status(BAD_REQUEST).entity("Party named $partyName cannot be found.\n").build()

        return try {
            val flowHandle = rpcOps.startTrackedFlow(::Initiator, itemId, otherParty, time, hourPrice, contractId)
            flowHandle.progress.subscribe { println(">> $it") }

            // The line below blocks and waits for the future to resolve.
            val result = flowHandle.returnValue.getOrThrow()

            val stateId = result.tx.outputsOfType<ItemState>().single().linearId.id

            Response.status(CREATED).entity(mapOf(
                    "stateId" to stateId
            )).build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(mapOf("error" to ex.message!!)).build()
        }
    }

    @GET
    @Path("return")
    @Produces(MediaType.APPLICATION_JSON)
    fun SettleLoan(@QueryParam(value = "state") itemId: String): Response {
        return try {
            val itemState = rpcOps.startFlow(::SettleFlow, itemId).returnValue.get()
            val stateId = itemState.tx.outputsOfType<ItemState>().single().linearId.id

            Response.status(CREATED).entity(mapOf(
                    "stateId" to stateId
            )).build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(mapOf("error" to ex.message!!)).build()
        }
    }
}