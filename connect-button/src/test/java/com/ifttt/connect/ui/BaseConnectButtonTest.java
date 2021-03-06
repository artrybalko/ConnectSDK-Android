package com.ifttt.connect.ui;

import android.net.Uri;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.ifttt.connect.R;
import com.ifttt.connect.api.Connection;
import com.ifttt.connect.api.ConnectionApiClient;
import com.ifttt.connect.api.ErrorResponse;
import com.ifttt.connect.api.TestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Okio;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static com.google.common.truth.Truth.assertThat;
import static com.ifttt.connect.api.TestUtils.loadConnection;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 28)
public final class BaseConnectButtonTest {

    private BaseConnectButton button;
    private ConnectionApiClient client;
    private CredentialsProvider credentialsProvider;

    @Before
    public void setUp() {
        ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class);
        scenario.onActivity(activity -> {
            button = activity.findViewById(R.id.ifttt_connect_button_test);
            client = new ConnectionApiClient.Builder(activity, () -> null).build();
        });

        credentialsProvider = new CredentialsProvider() {
            @Override
            public String getOAuthCode() {
                return null;
            }

            @Override
            public String getUserToken() {
                return null;
            }
        };
    }

    @Test
    public void initButton() {
        TextSwitcher connectText = button.findViewById(R.id.connect_with_ifttt);
        assertThat(connectText.getCurrentView()).isInstanceOf(TextView.class);
        assertThat(((TextView) connectText.getCurrentView()).getText()).isEqualTo("");

        ImageView iconImage = button.findViewById(R.id.ifttt_icon);
        assertThat(iconImage.getBackground()).isNull();

        TextSwitcher helperText = button.findViewById(R.id.ifttt_helper_text);
        assertThat(helperText.getCurrentView()).isInstanceOf(TextView.class);
        assertThat(((TextView) helperText.getCurrentView()).getText().toString()).isEqualTo("WORKS WITH IFTTT");
    }

    @Test(expected = IllegalStateException.class)
    public void testWithoutSetup() throws Exception {
        Connection connection = loadConnection(getClass().getClassLoader());
        button.setConnection(connection);
        fail();
    }

    @Test
    public void setConnection() throws IOException {
        Connection connection = loadConnection(getClass().getClassLoader());

        button.setup("a@b.com", client, Uri.parse("https://google.com"), credentialsProvider, null, false);
        button.setConnection(connection);

        TextSwitcher connectText = button.findViewById(R.id.connect_with_ifttt);
        assertThat(connectText.getCurrentView()).isInstanceOf(TextView.class);
        assertThat(((TextView) connectText.getCurrentView()).getText()).isEqualTo("Connect Twitter");

        TextSwitcher helperText = button.findViewById(R.id.ifttt_helper_text);
        assertThat(helperText.getCurrentView()).isInstanceOf(TextView.class);
    }

    @Test
    public void testOnDarkBackground() {
        TextSwitcher helperText = button.findViewById(R.id.ifttt_helper_text);
        TextView currentHelperTextView = (TextView) helperText.getCurrentView();
        TextView nextHelperTextView = (TextView) helperText.getNextView();

        button.setOnDarkBackground(true);
        assertThat(currentHelperTextView.getCurrentTextColor()).isEqualTo(ContextCompat.getColor(button.getContext(),
            R.color.ifttt_footer_text_white
        ));
        assertThat(nextHelperTextView.getCurrentTextColor()).isEqualTo(ContextCompat.getColor(button.getContext(),
            R.color.ifttt_footer_text_white
        ));

        button.setOnDarkBackground(false);
        assertThat(currentHelperTextView.getCurrentTextColor()).isEqualTo(ContextCompat.getColor(button.getContext(),
            R.color.ifttt_footer_text_black
        ));
        assertThat(nextHelperTextView.getCurrentTextColor()).isEqualTo(ContextCompat.getColor(button.getContext(),
            R.color.ifttt_footer_text_black
        ));
    }

    @Test
    public void testDispatchStates() throws IOException {
        button.setup("a@b.com", client, Uri.parse("https://google.com"), credentialsProvider, null, false);

        AtomicReference<ConnectButtonState> currentStateRef = new AtomicReference<>(ConnectButtonState.Initial);
        AtomicReference<ConnectButtonState> prevStateRef = new AtomicReference<>();
        AtomicReference<ErrorResponse> errorRef = new AtomicReference<>();
        button.addButtonStateChangeListener(new ButtonStateChangeListener() {
            @Override
            public void onStateChanged(
                ConnectButtonState currentState, ConnectButtonState previousState, Connection connection
            ) {
                currentStateRef.set(currentState);
                prevStateRef.set(previousState);
            }

            @Override
            public void onError(ErrorResponse errorResponse) {
                errorRef.set(errorResponse);
            }
        });

        Connection connection = loadConnection(getClass().getClassLoader());
        button.setConnection(connection);
        assertThat(currentStateRef.get()).isEqualTo(ConnectButtonState.Initial);

        button.setConnectResult(new ConnectResult(ConnectResult.NextStep.Error, null, "error"));
        assertThat(currentStateRef.get()).isEqualTo(ConnectButtonState.Initial);
        assertThat(errorRef.get()).isNotNull();
    }

    @Test
    public void testEnabledStateDispatch() throws IOException {
        AtomicReference<ConnectButtonState> currentStateRef = new AtomicReference<>();
        AtomicReference<ConnectButtonState> prevStateRef = new AtomicReference<>();

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("connection_enabled.json");
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(Okio.buffer(Okio.source(inputStream)).readUtf8()));
        server.start();

        button.setup("a@b.com",
            TestUtils.getMockConnectionApiClient(button.getContext(), server, () -> null),
            Uri.parse("https://google.com"),
            credentialsProvider,
            null,
            false
        );

        Connection connection = loadConnection(getClass().getClassLoader());
        button.setConnection(connection);
        button.addButtonStateChangeListener(new ButtonStateChangeListener() {
            @Override
            public void onStateChanged(
                ConnectButtonState currentState, ConnectButtonState previousState, Connection connection
            ) {
                currentStateRef.set(currentState);
                prevStateRef.set(previousState);
            }

            @Override
            public void onError(ErrorResponse errorResponse) {
            }
        });

        button.setConnectResult(new ConnectResult(ConnectResult.NextStep.Complete, "token", null));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertThat(currentStateRef.get()).isEqualTo(ConnectButtonState.Enabled);
        assertThat(prevStateRef.get()).isEqualTo(ConnectButtonState.Initial);
        server.shutdown();
    }
}
