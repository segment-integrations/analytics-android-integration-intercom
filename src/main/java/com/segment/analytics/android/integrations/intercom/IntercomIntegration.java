package com.segment.analytics.android.integrations.intercom;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.segment.analytics.Analytics;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;

import java.util.Map;

import io.intercom.android.sdk.Intercom;

/**
 * Intercom is your direct line of communication to every user, right inside your app. Intercom's
 * in-app messages are up to 10 times more effective than email too! Send the right messages, to the
 * right users, at exactly the right time.
 *
 * @see <a href="https://developers.intercom.com/v2.0/docs/android-installation">Intercom for
 *     Android</a>
 */
public class IntercomIntegration extends Integration<Void> {

  public static final Factory FACTORY =
      new Factory() {
        @Override
        public Integration<?> create(ValueMap settings, Analytics analytics) {
          Logger logger = analytics.logger(INTERCOM_KEY);

          Application application = analytics.getApplication();

          return new IntercomIntegration(application, settings, logger);
        }

        @Override
        public String key() {
          return INTERCOM_KEY;
        }
      };

  private static final String INTERCOM_KEY = "Intercom";
  private final Logger logger;

  public IntercomIntegration(Application application, ValueMap settings, Logger logger) {
    String apiKey = settings.getString("apiKey");
    String appId = settings.getString("appId");

    Intercom.initialize(application, apiKey, appId);
    this.logger = logger;
  }

  @Override
  public void identify(IdentifyPayload identify) {
    super.identify(identify);
  }

  @Override
  public void track(TrackPayload track) {
    super.track(track);
  }

  @Override
  public void group(GroupPayload group) {
    super.group(group);
  }
}
