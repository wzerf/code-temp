package com.wshake.infra.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExecutionMirrorScheduleBootstrapTest {

    @Test
    void createsMirrorScheduleWithoutCatchup() {
        ScheduleClient scheduleClient = mock(ScheduleClient.class);
        ScheduleHandle handle = mock(ScheduleHandle.class);
        when(scheduleClient.getHandle(ExecutionMirrorScheduleBootstrap.SCHEDULE_ID))
                .thenReturn(handle);
        when(handle.describe()).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        new ExecutionMirrorScheduleBootstrap(scheduleClient).run(null);

        ArgumentCaptor<Schedule> schedule = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleClient)
                .createSchedule(
                        eq(ExecutionMirrorScheduleBootstrap.SCHEDULE_ID),
                        schedule.capture(),
                        any(ScheduleOptions.class));
        assertThat(schedule.getValue().getPolicy().getCatchupWindow()).isEqualTo(Duration.ofSeconds(10));
    }
}
