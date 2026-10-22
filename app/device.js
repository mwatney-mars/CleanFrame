/* Android TV / Google TV bridge (`window.AndroidBridge`) with web preview fallback. */
(function () {
  'use strict';
  var IDS = {
    youtube: 'com.google.android.youtube.tv',
    netflix: 'com.netflix.ninja',
    disneyplus: 'com.disney.disneyplus',
    primevideo: 'com.amazon.amazonvideo.livingroom',
    appletv: 'com.apple.atve.androidtv.appletv',
    stremio: 'com.stremio.one',
    plex: 'com.plexapp.android',
    youtubemusic: 'com.google.android.youtube.tvmusic',
    applemusic: 'com.apple.android.music'
  };
  var EXTRA_BRANDS = {
    'com.spotify.tv.android': 'spotify',
    'com.spotify.music': 'spotify',
    'com.wbd.stream': 'hbomax',
    'com.hbo.hbonow': 'hbomax',
    'com.tubitv': 'tubi',
    'com.esaba.downloader': 'downloader',
    'au.net.abc.iview': 'abciview',
    'com.sbs.ondemand.tv': 'sbs'
  };
  var HIDDEN_PACKAGES = [
    'com.cleanframe.tv',
    'com.ansub.tvhome',
    'com.google.android.apps.tv.launcherx',
    'com.google.android.tungsten.setupwraith',
    'com.android.tv.settings'
  ];
  var DEFAULT_NAMES = {
    youtube: 'YouTube',
    netflix: 'Netflix',
    disneyplus: 'Disney+',
    primevideo: 'Prime Video',
    appletv: 'Apple TV',
    stremio: 'Stremio',
    plex: 'Plex',
    youtubemusic: 'YouTube Music',
    applemusic: 'Apple Music'
  };

  function bridge() { return window.AndroidBridge || null; }
  function unavailable(done) {
    done({ message: 'Open CleanFrame on the TV to use this control.', detail: 'The web preview cannot launch external TV apps.' });
  }
  function failure(detail) {
    return { message: 'The TV could not open this app.', detail: detail || 'Check that the app is installed and enabled on your TV.' };
  }

  function launchApp(id, done) {
    var b = bridge();
    if (!b || typeof b.launchApp !== 'function') { unavailable(done); return; }
    try {
      var ok = b.launchApp(String(id || ''));
      done(ok ? null : failure('Package ' + id + ' could not be started.'));
    } catch (err) {
      done(failure(err && err.message));
    }
  }

  function launch(brand, done) {
    if (!IDS[brand]) { done({ message: 'This app has no launch target.' }); return; }
    launchApp(IDS[brand], done);
  }

  function art(done) {
    if (typeof window.CleanFrameEnterArtMode === 'function') {
      window.CleanFrameEnterArtMode();
      done(null);
      return;
    }
    done(null);
  }

  function home(done) {
    var b = bridge();
    if (!b || typeof b.openGoogleTvHome !== 'function') { unavailable(done); return; }
    try {
      var ok = b.openGoogleTvHome();
      done(ok ? null : failure('Could not open default TV Home.'));
    } catch (err) {
      done(failure(err && err.message));
    }
  }

  function settings(done) {
    var b = bridge();
    if (!b || typeof b.openTvSettings !== 'function') { unavailable(done); return; }
    try {
      var ok = b.openTvSettings();
      done(ok ? null : failure('Could not open TV Settings.'));
    } catch (err) {
      done(failure(err && err.message));
    }
  }

  function readInstalledSync() {
    var b = bridge(), known = {}, seen = {}, result = [];
    var brandOrder = Object.keys(IDS);
    brandOrder.forEach(function (brand) { known[IDS[brand]] = brand; });
    if (!b || typeof b.getInstalledApps !== 'function') return null;
    try {
      var raw = b.getInstalledApps();
      var list = JSON.parse(raw || '[]');
      list.forEach(function (a) {
        if (!a || !a.appId || seen[a.appId] || HIDDEN_PACKAGES.indexOf(a.appId) >= 0) return;
        var brand = known[a.appId] || EXTRA_BRANDS[a.appId] || '';
        var primaryIdx = brand ? brandOrder.indexOf(brand) : -1;
        seen[a.appId] = true;
        result.push({
          appId: a.appId,
          name: primaryIdx >= 0 ? DEFAULT_NAMES[brand] : (a.name || a.appId),
          brand: brand,
          icon: a.icon || '',
          rank: primaryIdx >= 0 ? primaryIdx : (brand ? 12 : 20)
        });
      });
      result.sort(function (a, b) { return a.rank - b.rank || a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
      return result;
    } catch (err) {
      return null;
    }
  }

  function apps(done) {
    var installed = readInstalledSync();
    if (installed) {
      done(null, installed);
      return;
    }
    if (bridge()) {
      done(failure('Could not read installed apps from AndroidBridge.'));
      return;
    }
    var brandOrder = Object.keys(IDS), result = [];
    brandOrder.forEach(function (brand, idx) {
      result.push({
        appId: IDS[brand],
        name: DEFAULT_NAMES[brand],
        brand: brand,
        icon: '',
        rank: idx
      });
    });
    done(null, result);
  }

  window.FrameDevice = {
    launch: launch,
    launchApp: launchApp,
    art: art,
    home: home,
    settings: settings,
    apps: apps,
    initialApps: readInstalledSync
  };
}());
