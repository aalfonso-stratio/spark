/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved
 *
 * This software is a modification of the original software Apache Spark licensed under the Apache 2.0
 * license, a copy of which is below. This software contains proprietary information of
 * Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or
 * otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled,
 * without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.scheduler

import org.apache.spark.{LocalSparkContext, SparkConf, SparkContext, SparkFunSuite}
import org.apache.spark.scheduler.SchedulingMode.SchedulingMode
import org.apache.spark.storage.BlockManagerId
import org.apache.spark.util.AccumulatorV2

class ExternalClusterManagerSuite extends SparkFunSuite with LocalSparkContext {
  test("launch of backend and scheduler") {
    val conf = new SparkConf().setMaster("myclusterManager").
        setAppName("testcm").set("spark.driver.allowMultipleContexts", "true")
    sc = new SparkContext(conf)
    // check if the scheduler components are created and initialized
    sc.schedulerBackend match {
      case dummy: DummySchedulerBackend => assert(dummy.initialized)
      case other => fail(s"wrong scheduler backend: ${other}")
    }
    sc.taskScheduler match {
      case dummy: DummyTaskScheduler => assert(dummy.initialized)
      case other => fail(s"wrong task scheduler: ${other}")
    }
  }
}

/**
 * Super basic ExternalClusterManager, just to verify ExternalClusterManagers can be configured.
 *
 * Note that if you want a special ClusterManager for tests, you are probably much more interested
 * in [[MockExternalClusterManager]] and the corresponding [[SchedulerIntegrationSuite]]
 */
private class DummyExternalClusterManager extends ExternalClusterManager {

  def canCreate(masterURL: String): Boolean = masterURL == "myclusterManager"

  def createTaskScheduler(sc: SparkContext,
      masterURL: String): TaskScheduler = new DummyTaskScheduler

  def createSchedulerBackend(sc: SparkContext,
      masterURL: String,
      scheduler: TaskScheduler): SchedulerBackend = new DummySchedulerBackend()

  def initialize(scheduler: TaskScheduler, backend: SchedulerBackend): Unit = {
    scheduler.asInstanceOf[DummyTaskScheduler].initialized = true
    backend.asInstanceOf[DummySchedulerBackend].initialized = true
  }

}

private class DummySchedulerBackend extends SchedulerBackend {
  var initialized = false
  def start() {}
  def stop() {}
  def reviveOffers() {}
  def defaultParallelism(): Int = 1
}

private class DummyTaskScheduler extends TaskScheduler {
  var initialized = false
  override def rootPool: Pool = null
  override def schedulingMode: SchedulingMode = SchedulingMode.NONE
  override def start(): Unit = {}
  override def stop(): Unit = {}
  override def submitTasks(taskSet: TaskSet): Unit = {}
  override def cancelTasks(stageId: Int, interruptThread: Boolean): Unit = {}
  override def setDAGScheduler(dagScheduler: DAGScheduler): Unit = {}
  override def defaultParallelism(): Int = 2
  override def executorLost(executorId: String, reason: ExecutorLossReason): Unit = {}
  override def applicationAttemptId(): Option[String] = None
  def executorHeartbeatReceived(
      execId: String,
      accumUpdates: Array[(Long, Seq[AccumulatorV2[_, _]])],
      blockManagerId: BlockManagerId): Boolean = true
}
