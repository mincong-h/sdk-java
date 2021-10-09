package io.temporal.serviceclient;

import com.google.protobuf.Duration;
import io.temporal.api.common.v1.Payload;
import io.temporal.api.common.v1.Payloads;
import io.temporal.api.common.v1.WorkflowType;
import io.temporal.api.taskqueue.v1.TaskQueue;
import io.temporal.api.workflowservice.v1.StartWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.StartWorkflowExecutionResponse;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.DataConverter;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BrokenServiceClientTest {
  private TestWorkflowEnvironment testEnv;

  @Before
  public void setUp() {
    // server side
    WorkerFactoryOptions options = WorkerFactoryOptions.newBuilder().build();

    // client side
    WorkflowClientOptions clientOptions =
        WorkflowClientOptions.newBuilder().setNamespace("junit").build();

    // environment
    TestEnvironmentOptions testOptions =
        TestEnvironmentOptions.newBuilder()
            .setWorkflowClientOptions(clientOptions)
            .setWorkerFactoryOptions(options)
            .build();
    testEnv = TestWorkflowEnvironment.newInstance(testOptions);

    // factory maintains worker creation and lifecycle
    WorkerFactory factory = testEnv.getWorkerFactory();

    Worker worker =
        factory.newWorker("my-worker/junit.localhost", WorkerOptions.newBuilder().build());
    worker.registerWorkflowImplementationTypes(SimpleWorkflowImpl.class);

    factory.start();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testServiceClient() {
    WorkflowServiceStubs clientService = null;
    try {
      clientService = WorkflowServiceStubs.newInstance();
      StartWorkflowExecutionResponse response =
          clientService
              .blockingStub()
              .startWorkflowExecution(newRequest(new SimpleWorkflowParams("JUnit")));
      Assert.assertNotNull(response.getRunId());
    } catch (Exception e) {
      Assert.fail("Error occurred: " + e.getMessage());
    } finally {
      if (clientService != null) {
        clientService.shutdown();
      }
    }
  }

  private StartWorkflowExecutionRequest newRequest(SimpleWorkflowParams params) {
    Payload payload = DataConverter.getDefaultInstance().toPayload(params).get();
    return StartWorkflowExecutionRequest.newBuilder()
        .setNamespace("junit")
        .setWorkflowId("simple-workflow")
        .setWorkflowType(WorkflowType.newBuilder().setName("SimpleWorkflow").build())
        .setTaskQueue(TaskQueue.newBuilder().setName("my-worker/junit.localhost").build())
        .setWorkflowExecutionTimeout(Duration.newBuilder().setSeconds(60).build())
        .setInput(Payloads.newBuilder().addPayloads(payload).build())
        .build();
  }

  public static class SimpleWorkflowParams {
    private final String sender;

    public SimpleWorkflowParams(String sender) {
      this.sender = sender;
    }
  }

  @WorkflowInterface
  public interface SimpleWorkflow {
    @WorkflowMethod
    String execute(SimpleWorkflowParams params);
  }

  public static class SimpleWorkflowImpl implements SimpleWorkflow {

    @Override
    public String execute(SimpleWorkflowParams params) {
      return "Hi, " + params.sender;
    }
  }
}
