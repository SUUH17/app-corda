//package com.example.flow
//
//import com.example.state.ItemState
//import net.corda.core.contracts.TransactionVerificationException
//import net.corda.core.node.services.queryBy
//import net.corda.core.utilities.getOrThrow
//import net.corda.node.internal.StartedNode
//import net.corda.testing.chooseIdentity
//import net.corda.testing.node.MockNetwork
//import net.corda.testing.setCordappPackages
//import net.corda.testing.unsetCordappPackages
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import kotlin.test.assertEquals
//import kotlin.test.assertFailsWith
//
//class IOUFlowTests {
//    lateinit var network: MockNetwork
//    lateinit var a: StartedNode<MockNetwork.MockNode>
//    lateinit var b: StartedNode<MockNetwork.MockNode>
//
//    @Before
//    fun setup() {
//        setCordappPackages("com.example.contract")
//        network = MockNetwork()
//        val nodes = network.createSomeNodes(2)
//        a = nodes.partyNodes[0]
//        b = nodes.partyNodes[1]
//        // For real nodes this happens automatically, but we have to manually register the flow for tests.
//        nodes.partyNodes.forEach { it.registerInitiatedFlow(BorrowFlow.Acceptor::class.java) }
//        network.runNetwork()
//    }
//
//    @After
//    fun tearDown() {
//        unsetCordappPackages()
//        network.stopNodes()
//    }
//
//    @Test
//    fun `flow rejects invalid IOUs`() {
//        val flow = BorrowFlow.Initiator(-1, b.info.chooseIdentity())
//        val future = a.services.startFlow(flow).resultFuture
//        network.runNetwork()
//
//        // The ItemContract specifies that IOUs cannot have negative values.
//        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
//    }
//
//    @Test
//    fun `SignedTransaction returned by the flow is signed by the initiator`() {
//        val flow = BorrowFlow.Initiator(1, b.info.chooseIdentity())
//        val future = a.services.startFlow(flow).resultFuture
//        network.runNetwork()
//
//        val signedTx = future.getOrThrow()
//        signedTx.verifySignaturesExcept(b.info.chooseIdentity().owningKey)
//    }
//
//    @Test
//    fun `SignedTransaction returned by the flow is signed by the acceptor`() {
//        val flow = BorrowFlow.Initiator(1, b.info.chooseIdentity())
//        val future = a.services.startFlow(flow).resultFuture
//        network.runNetwork()
//
//        val signedTx = future.getOrThrow()
//        signedTx.verifySignaturesExcept(a.info.chooseIdentity().owningKey)
//    }
//
//    @Test
//    fun `flow records a transaction in both parties' transaction storages`() {
//        val flow = BorrowFlow.Initiator(1, b.info.chooseIdentity())
//        val future = a.services.startFlow(flow).resultFuture
//        network.runNetwork()
//        val signedTx = future.getOrThrow()
//
//        // We check the recorded transaction in both transaction storages.
//        for (node in listOf(a, b)) {
//            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
//        }
//    }
//
//    @Test
//    fun `recorded transaction has no inputs and a single output, the input IOU`() {
//        val iouValue = 1
//        val flow = BorrowFlow.Initiator(iouValue, b.info.chooseIdentity())
//        val future = a.services.startFlow(flow).resultFuture
//        network.runNetwork()
//        val signedTx = future.getOrThrow()
//
//        // We check the recorded transaction in both vaults.
//        for (node in listOf(a, b)) {
//            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
//            val txOutputs = recordedTx!!.tx.outputs
//            assert(txOutputs.size == 1)
//
//            val recordedState = txOutputs[0].data as ItemState
//            assertEquals(recordedState.value, iouValue)
//            assertEquals(recordedState.lender, a.info.chooseIdentity())
//            assertEquals(recordedState.borrower, b.info.chooseIdentity())
//        }
//    }
//
//    @Test
//    fun `flow records the correct IOU in both parties' vaults`() {
//        val iouValue = 1
//        val flow = BorrowFlow.Initiator(1, b.info.chooseIdentity())
//        val future = a.services.startFlow(flow).resultFuture
//        network.runNetwork()
//        future.getOrThrow()
//
//        // We check the recorded IOU in both vaults.
//        for (node in listOf(a, b)) {
//            node.database.transaction {
//                val ious = node.services.vaultService.queryBy<ItemState>().states
//                assertEquals(1, ious.size)
//                val recordedState = ious.single().state.data
//                assertEquals(recordedState.value, iouValue)
//                assertEquals(recordedState.lender, a.info.chooseIdentity())
//                assertEquals(recordedState.borrower, b.info.chooseIdentity())
//            }
//        }
//    }
//}
