/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.worker;

import static io.temporal.testing.internal.SDKTestWorkflowRule.NAMESPACE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.temporal.api.common.v1.Payloads;
import io.temporal.api.common.v1.WorkflowType;
import io.temporal.api.taskqueue.v1.TaskQueue;
import io.temporal.api.workflowservice.v1.StartWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.StartWorkflowExecutionResponse;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.converter.DataConverter;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServiceClientTests {
  private TestWorkflowEnvironment testEnv;

  @Before
  public void setUp() {
    TestEnvironmentOptions testOptions =
        TestEnvironmentOptions.newBuilder()
            // server
            .setWorkerFactoryOptions(WorkerFactoryOptions.getDefaultInstance())
            // client
            .setWorkflowClientOptions(
                WorkflowClientOptions.newBuilder().setNamespace(NAMESPACE).build())
            .build();
    testEnv = TestWorkflowEnvironment.newInstance(testOptions);
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testNewUntypedWorkflowStub() {
    // Arrange
    String taskQueueName = "veryLongWorkflow";

    // setup server
    WorkerFactory factory = testEnv.getWorkerFactory();
    Worker worker = factory.newWorker(taskQueueName);
    worker.registerWorkflowImplementationTypes(ActivitiesWorkflowImpl.class);
    factory.start();

    // setup client
    WorkflowOptions workflowOptions =
        WorkflowOptions.newBuilder().setTaskQueue(taskQueueName).build();
    WorkflowStub workflow =
        testEnv.getWorkflowClient().newUntypedWorkflowStub("ActivitiesWorkflow", workflowOptions);

    WorkflowParams params = new WorkflowParams();
    params.sender = "JUnit";
    workflow.start(params);

    assertEquals("I'm done, JUnit", workflow.getResult(String.class));
  }

  @Test
  public void testBlockingStub() {
    // Arrange
    String taskQueueName = "veryLongWorkflow";

    // setup server
    WorkerFactory factory = testEnv.getWorkerFactory();
    Worker worker = factory.newWorker(taskQueueName);
    worker.registerWorkflowImplementationTypes(ActivitiesWorkflowImpl.class);
    factory.start();

    // setup client
    WorkflowParams params = new WorkflowParams();
    params.sender = "JUnit";

    StartWorkflowExecutionRequest request =
        StartWorkflowExecutionRequest.newBuilder()
            .setRequestId("myId")
            .setWorkflowType(WorkflowType.newBuilder().setName("ActivitiesWorkflow").build())
            .setInput(
                Payloads.newBuilder()
                    .addPayloads(DataConverter.getDefaultInstance().toPayload(params).get())
                    .build())
            .setTaskQueue(TaskQueue.newBuilder().setName(taskQueueName).build())
            .setNamespace(NAMESPACE)
            .build();
    StartWorkflowExecutionResponse response =
        testEnv
            .getWorkflowClient()
            .getWorkflowServiceStubs()
            .blockingStub()
            .startWorkflowExecution(request);
    assertNotNull(response.getRunId());
  }

  /* ----- workflow ----- */

  public static class WorkflowParams {
    public String sender;
  }

  @WorkflowInterface
  public interface ActivitiesWorkflow {

    @WorkflowMethod
    String execute(WorkflowParams params);
  }

  public static class ActivitiesWorkflowImpl implements ActivitiesWorkflow {

    @Override
    public String execute(WorkflowParams params) {
      return "I'm done, " + params.sender;
    }
  }
}
