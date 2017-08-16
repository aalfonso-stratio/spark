/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved
 *
 * This software is a modification of the original software Apache Spark licensed under the Apache 2.0
 * license, a copy of which is below. This software contains proprietary information of
 * Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or
 * otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled,
 * without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.sql.execution

import org.apache.spark.SparkContext
import org.apache.spark.sql._
import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.execution.datasources.{DataSourceStrategy, FileSourceStrategy}
import org.apache.spark.sql.internal.SQLConf

class SparkPlanner(
    val sparkContext: SparkContext,
    val conf: SQLConf,
    val extraStrategies: Seq[Strategy])
  extends SparkStrategies {

  def numPartitions: Int = conf.numShufflePartitions

  def strategies: Seq[Strategy] =
      extraStrategies ++ (
      FileSourceStrategy ::
      DataSourceStrategy ::
      DDLStrategy ::
      SpecialLimits ::
      Aggregation ::
      JoinSelection ::
      InMemoryScans ::
      BasicOperators :: Nil)

  override protected def collectPlaceholders(plan: SparkPlan): Seq[(SparkPlan, LogicalPlan)] = {
    plan.collect {
      case placeholder @ PlanLater(logicalPlan) => placeholder -> logicalPlan
    }
  }

  override protected def prunePlans(plans: Iterator[SparkPlan]): Iterator[SparkPlan] = {
    // TODO: We will need to prune bad plans when we improve plan space exploration
    //       to prevent combinatorial explosion.
    plans
  }

  /**
   * Used to build table scan operators where complex projection and filtering are done using
   * separate physical operators.  This function returns the given scan operator with Project and
   * Filter nodes added only when needed.  For example, a Project operator is only used when the
   * final desired output requires complex expressions to be evaluated or when columns can be
   * further eliminated out after filtering has been done.
   *
   * The `prunePushedDownFilters` parameter is used to remove those filters that can be optimized
   * away by the filter pushdown optimization.
   *
   * The required attributes for both filtering and expression evaluation are passed to the
   * provided `scanBuilder` function so that it can avoid unnecessary column materialization.
   */
  def pruneFilterProject(
      projectList: Seq[NamedExpression],
      filterPredicates: Seq[Expression],
      prunePushedDownFilters: Seq[Expression] => Seq[Expression],
      scanBuilder: Seq[Attribute] => SparkPlan): SparkPlan = {

    val projectSet = AttributeSet(projectList.flatMap(_.references))
    val filterSet = AttributeSet(filterPredicates.flatMap(_.references))
    val filterCondition: Option[Expression] =
      prunePushedDownFilters(filterPredicates).reduceLeftOption(catalyst.expressions.And)

    // Right now we still use a projection even if the only evaluation is applying an alias
    // to a column.  Since this is a no-op, it could be avoided. However, using this
    // optimization with the current implementation would change the output schema.
    // TODO: Decouple final output schema from expression evaluation so this copy can be
    // avoided safely.

    if (AttributeSet(projectList.map(_.toAttribute)) == projectSet &&
        filterSet.subsetOf(projectSet)) {
      // When it is possible to just use column pruning to get the right projection and
      // when the columns of this projection are enough to evaluate all filter conditions,
      // just do a scan followed by a filter, with no extra project.
      val scan = scanBuilder(projectList.asInstanceOf[Seq[Attribute]])
      filterCondition.map(FilterExec(_, scan)).getOrElse(scan)
    } else {
      val scan = scanBuilder((projectSet ++ filterSet).toSeq)
      ProjectExec(projectList, filterCondition.map(FilterExec(_, scan)).getOrElse(scan))
    }
  }
}
