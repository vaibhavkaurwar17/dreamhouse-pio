import java.nio.file.Files

import io.prediction.controller.{EmptyParams, EngineParams}
import io.prediction.data.storage.EngineInstance
import io.prediction.workflow.CreateWorkflow.WorkflowConfig
import io.prediction.workflow._
import org.joda.time.DateTime

object TrainApp extends App {

  def train() = {
    // WTF: envs must not be empty or CreateServer.engineInstances.get... fails due to JDBCUtils.stringToMap
    val envs = Map("FOO" -> "BAR")

    val sparkEnv = Map("spark.master" -> "local")

    // WTF: envs must not be empty or CreateServer.engineInstances.get... fails due to JDBCUtils.stringToMap
    val sparkConf = Map("spark.executor.extraClassPath" -> ".")

    val engineFactoryName = "RecommendationEngine"

    val workflowConfig: WorkflowConfig = CreateWorkflow.WorkflowConfig(
      engineId = EngineConfig.engineId,
      engineVersion = EngineConfig.engineVersion,
      engineVariant = EngineConfig.engineVariantId,
      engineFactory = engineFactoryName
    )

    val workflowParams = WorkflowParams(
      verbose = workflowConfig.verbosity,
      skipSanityCheck = workflowConfig.skipSanityCheck,
      stopAfterRead = workflowConfig.stopAfterRead,
      stopAfterPrepare = workflowConfig.stopAfterPrepare,
      sparkEnv = WorkflowParams().sparkEnv ++ sparkEnv
    )

    WorkflowUtils.modifyLogging(workflowConfig.verbose)

    val dataSourceParams = DataSourceParams(sys.env.get("DREAMHOUSE_WEB_APP_URL").get)
    val preparatorParams = EmptyParams()
    val algorithmParamsList = Seq("als" -> AlgorithmParams(rank = 10, numIterations = 10, lambda = 0.01, tmpDir = Files.createTempDirectory("model").toFile.getAbsolutePath))
    val servingParams = EmptyParams()

    val engineInstance = EngineInstance(
      id = "",
      status = "INIT",
      startTime = DateTime.now,
      endTime = DateTime.now,
      engineId = workflowConfig.engineId,
      engineVersion = workflowConfig.engineVersion,
      engineVariant = workflowConfig.engineVariant,
      engineFactory = workflowConfig.engineFactory,
      batch = workflowConfig.batch,
      env = envs,
      sparkConf = sparkConf,
      dataSourceParams = JsonExtractor.paramToJson(workflowConfig.jsonExtractor, workflowConfig.engineParamsKey -> dataSourceParams),
      preparatorParams = JsonExtractor.paramToJson(workflowConfig.jsonExtractor, workflowConfig.engineParamsKey -> preparatorParams),
      algorithmsParams = JsonExtractor.paramsToJson(workflowConfig.jsonExtractor, algorithmParamsList),
      servingParams = JsonExtractor.paramToJson(workflowConfig.jsonExtractor, workflowConfig.engineParamsKey -> servingParams)
    )

    val (_, engineFactory) = WorkflowUtils.getEngine(engineInstance.engineFactory, getClass.getClassLoader)

    val engine = engineFactory()

    val engineParams = EngineParams(
      dataSourceParams = dataSourceParams,
      preparatorParams = preparatorParams,
      algorithmParamsList = algorithmParamsList,
      servingParams = servingParams
    )

    val engineInstanceId = CreateServer.engineInstances.insert(engineInstance)

    CoreWorkflow.runTrain(
      env = envs,
      params = workflowParams,
      engine = engine,
      engineParams = engineParams,
      engineInstance = engineInstance.copy(id = engineInstanceId)
    )

  }

  train()

  CreateServer.actorSystem.shutdown()

}
