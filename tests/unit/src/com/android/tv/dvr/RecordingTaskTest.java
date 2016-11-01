/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tv.dvr;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.test.AndroidTestCase;

import com.android.tv.InputSessionManager;
import com.android.tv.InputSessionManager.RecordingSession;
import com.android.tv.data.Channel;
import com.android.tv.dvr.RecordingTask.State;
import com.android.tv.testing.FakeClock;
import com.android.tv.testing.dvr.RecordingTestUtils;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link RecordingTask}.
 */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class RecordingTaskTest extends AndroidTestCase {
    private static final long DURATION = TimeUnit.MINUTES.toMillis(30);
    private static final long START_OFFSET_MS = Scheduler.MS_TO_WAKE_BEFORE_START;
    private static final String INPUT_ID = "input_id";
    private static final int CHANNEL_ID = 273;

    private FakeClock mFakeClock;
    private DvrDataManagerInMemoryImpl mDataManager;
    @Mock Handler mMockHandler;
    @Mock DvrManager mDvrManager;
    @Mock InputSessionManager mMockSessionManager;
    @Mock RecordingSession mMockRecordingSession;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        MockitoAnnotations.initMocks(this);
        mFakeClock = FakeClock.createWithCurrentTime();
        mDataManager = new DvrDataManagerInMemoryImpl(getContext(), mFakeClock);
    }

    public void testHandle_init() {
        Channel channel = createTestChannel();
        ScheduledRecording r = createRecording(channel);
        RecordingTask task = createRecordingTask(r, channel);
        String inputId = channel.getInputId();
        when(mMockSessionManager.createRecordingSession(eq(inputId), anyString(), eq(task),
                eq(mMockHandler), anyLong())).thenReturn(mMockRecordingSession);
        when(mMockHandler.sendMessageAtTime(anyObject(), anyLong())).thenReturn(true);
        assertTrue(task.handleMessage(createMessage(RecordingTask.MSG_INITIALIZE)));
        assertEquals(State.CONNECTION_PENDING, task.getState());
        verify(mMockSessionManager).createRecordingSession(eq(inputId), anyString(), eq(task),
                eq(mMockHandler), anyLong());
        verify(mMockRecordingSession).tune(eq(inputId), eq(channel.getUri()));
        verifyNoMoreInteractions(mMockHandler, mMockRecordingSession, mMockSessionManager);
    }

    private static Channel createTestChannel() {
        return new Channel.Builder().setInputId(INPUT_ID).setId(CHANNEL_ID)
                .setDisplayName("Test Ch " + CHANNEL_ID).build();
    }

    public void testOnConnected() {
        Channel channel = createTestChannel();
        ScheduledRecording r = createRecording(channel);
        mDataManager.addScheduledRecording(r);
        RecordingTask task = createRecordingTask(r, channel);
        String inputId = channel.getInputId();
        when(mMockSessionManager.createRecordingSession(eq(inputId), anyString(), eq(task),
                eq(mMockHandler), anyLong())).thenReturn(mMockRecordingSession);
        when(mMockHandler.sendMessageAtTime(anyObject(), anyLong())).thenReturn(true);
        task.handleMessage(createMessage(RecordingTask.MSG_INITIALIZE));
        task.onTuned(channel.getUri());
        assertEquals(State.CONNECTED, task.getState());
    }

    private ScheduledRecording createRecording(Channel c) {
        long startTime = mFakeClock.currentTimeMillis() + START_OFFSET_MS;
        long endTime = startTime + DURATION;
        return RecordingTestUtils.createTestRecordingWithPeriod(c.getInputId(), c.getId(),
                startTime, endTime);
    }

    private RecordingTask createRecordingTask(ScheduledRecording r, Channel channel) {
        RecordingTask recordingTask = new RecordingTask(getContext(), r, channel, mDvrManager,
                mMockSessionManager, mDataManager, mFakeClock);
        recordingTask.setHandler(mMockHandler);
        return recordingTask;
    }

    private void verifySendMessageAt(int what, long when) {
        verify(mMockHandler).sendMessageAtTime(argThat(messageMatchesWhat(what)), delta(when, 100));
    }

    private static long delta(final long value, final long delta) {
        return longThat(new BaseMatcher<Long>() {
            @Override
            public boolean matches(Object item) {
                Long other = (Long) item;
                return other >= value - delta && other <= value + delta;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("eq " + value + "±" + delta);

            }
        });
    }

    private Message createMessage(int what) {
        Message msg = new Message();
        msg.setTarget(mMockHandler);
        msg.what = what;
        return msg;
    }

    private static ArgumentMatcher<Message> messageMatchesWhat(final int what) {
        return new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Object argument) {
                Message message = (Message) argument;
                return message.what == what;
            }
        };
    }
}