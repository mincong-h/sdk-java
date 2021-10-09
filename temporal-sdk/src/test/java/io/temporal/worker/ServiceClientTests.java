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

import io.temporal.activity.ActivityInterface;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceClientTests {
  private TestWorkflowEnvironment testEnv;

  @Before
  public void setUp() {
    WorkerFactoryOptions options =
        WorkerFactoryOptions.newBuilder(
                WorkerFactoryOptions.newBuilder().setMaxWorkflowThreadCount(200).build())
            .validateAndBuildWithDefaults();
    WorkflowClientOptions clientOptions =
        WorkflowClientOptions.newBuilder().setNamespace(NAMESPACE).build();
    TestEnvironmentOptions testOptions =
        TestEnvironmentOptions.newBuilder()
            .setWorkflowClientOptions(clientOptions)
            .setWorkerFactoryOptions(options)
            .build();
    testEnv = TestWorkflowEnvironment.newInstance(testOptions);
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void longHistoryWorkflowsCompleteSuccessfully() {
    // Arrange
    String taskQueueName = "veryLongWorkflow";

    // setup server
    WorkerFactory factory = testEnv.getWorkerFactory();
    Worker worker = factory.newWorker(taskQueueName, WorkerOptions.newBuilder().build());
    worker.registerWorkflowImplementationTypes(ActivitiesWorkflowImpl.class);
    worker.registerActivitiesImplementations(new ActivitiesImpl());
    factory.start();

    // setup client
    WorkflowOptions workflowOptions =
        WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueueName)
            .setWorkflowRunTimeout(Duration.ofSeconds(250))
            .setWorkflowTaskTimeout(Duration.ofSeconds(30))
            .build();
    WorkflowStub workflow =
        testEnv.getWorkflowClient().newUntypedWorkflowStub("ActivitiesWorkflow", workflowOptions);

    // Act
    // This will yield around 10000 events which is above the page limit returned by the server.
    WorkflowParams w = new WorkflowParams();
    w.TemporalSleepSeconds = 0;
    w.ChainSequence = 50;
    w.ConcurrentCount = 50;
    w.PayloadSizeBytes = 10000;
    w.TaskQueueName = taskQueueName;
    w.sender = "JUnit";

    workflow.start(w);
    assertEquals("I'm done, JUnit", workflow.getResult(String.class));
  }

  public static class WorkflowParams {
    public String sender;
    public int ChainSequence;
    public int ConcurrentCount;
    public String TaskQueueName;
    public int PayloadSizeBytes;
    public int TemporalSleepSeconds;
  }

  @WorkflowInterface
  public interface ActivitiesWorkflow {

    @WorkflowMethod()
    String execute(WorkflowParams params);
  }

  public static class ActivitiesWorkflowImpl implements ActivitiesWorkflow {

    @Override
    public String execute(WorkflowParams params) {
      return "I'm done, " + params.sender;
    }
  }

  @ActivityInterface
  public interface SleepActivity {
    void sleep(int chain, int concurrency, byte[] bytes);
  }

  public static class ActivitiesImpl implements SleepActivity {
    private static final Logger log = LoggerFactory.getLogger("sleep-activity");

    @Override
    public void sleep(int chain, int concurrency, byte[] bytes) {
      log.trace("sleep called");
    }
  }
}
