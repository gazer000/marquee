/*
 * The app list: every launch point on the TV, in the order and with the visibility saved on the
 * Customize screen, plus the HDMI inputs with something plugged in, which Marquee shows in their own
 * column. Ported from Marquee's AppStore.
 */
var Apps = (function () {
    var SELF = 'com.marquee.webos';
    var SETTINGS = 'com.palm.app.settings';
    var PREFS_KEY = 'marquee.prefs';
    // Shown on first run, in this order, when installed. Everything else is one Customize away.
    var DEFAULTS = [/^youtube\.leanback/, /limelight|moonlight|artemis/i, /^netflix$/, /^amazon$/,
        /disney/i, /^spotify/];
    // LG system screens that are launch points but not apps anyone opens from a home screen.
    var SKIP = /^com\.webos\.app\.(home|discovery|notificationcenter|livemenuplayer|inputcommon)$|^com\.marquee\.webos$/;

    function prefs() {
        try {
            var p = JSON.parse(localStorage.getItem(PREFS_KEY));
            if (p && p.order && p.shown) return p;
        } catch (e) { /* first run */ }
        return null;
    }

    function savePrefs(p) {
        try { localStorage.setItem(PREFS_KEY, JSON.stringify(p)); } catch (e) { /* ignore */ }
    }

    /** Calls a Luna method directly, and with root through Homebrew Channel if the TV refuses. */
    function ask(uri, params, callback) {
        Luna.call(uri, params, function (res) {
            if (res && res.returnValue) { callback(res); return; }
            Luna.rootCall(uri, params, function (rootRes) { callback(rootRes || res || {}); });
        });
    }

    function fileUrl(path) {
        if (!path) return null;
        return /^[a-z]+:/i.test(path) ? path : 'file://' + path;
    }

    function inputLabel(d) {
        var label = d.label || '', sub = d.subList && d.subList[0];
        // A name the user typed wins. Otherwise the device's own name: "PS5" from LG's device lookup
        // (its label adds a translated "game console"), or "Chromecast" over HDMI-CEC.
        if (d.modified && label) return label;
        if (sub && (sub.labelName || sub.osdName)) return sub.labelName || sub.osdName;
        if (d.spdProductDescription) return d.spdProductDescription;
        return label || d.id;
    }

    /** callback(every app except Settings, the Settings launch point, the HDMI inputs in port order). */
    function loadAll(callback) {
        var lps = null, inputs = null;
        function finish() {
            if (lps === null || inputs === null) return;
            var all = [], seen = {}, settings = null, hdmi = [];
            for (var i = 0; i < lps.length; i++) {
                var lp = lps[i], id = lp.launchPointId || lp.id;
                if (!lp.id || seen[id]) continue;
                if (lp.id === SETTINGS) { settings = lp; continue; }
                if (SKIP.test(lp.id)) continue;
                seen[id] = true;
                // HDMI inputs go in their own column. They come from the input service below, with the
                // devices' names; the launch points are only a fallback if that service didn't answer.
                if (/^com\.webos\.app\.hdmi\d$/.test(lp.id)) {
                    if (!inputs.length) hdmi.push({ key: 'input:' + lp.id, id: lp.id, title: lp.title || lp.id, input: true });
                    continue;
                }
                all.push({ key: id, id: lp.id, title: lp.title || lp.id, params: lp.params || null,
                    icon: fileUrl(lp.icon || lp.largeIcon), iconColor: lp.iconColor || lp.bgColor });
            }
            for (var j = 0; j < inputs.length; j++) {
                var d = inputs[j];
                if (!d.appId || !(d.connected || d.hdmiPlugIn)) continue;
                hdmi.push({ key: 'input:' + d.id, id: d.appId, title: inputLabel(d), params: null, icon: null, input: true });
            }
            callback(all, settings, hdmi);
        }
        ask('luna://com.webos.applicationManager/listLaunchPoints', {}, function (res) {
            lps = res.launchPoints || [];
            if (lps.length) { finish(); return; }
            ask('luna://com.webos.applicationManager/listApps', {}, function (r2) {
                lps = [];
                var apps = r2.apps || [];
                for (var k = 0; k < apps.length; k++) {
                    if (apps[k].visible === false || apps[k].type === 'stub') continue;
                    lps.push(apps[k]);
                }
                finish();
            });
        });
        ask('luna://com.webos.service.eim/getAllInputStatus', {}, function (res) {
            var list = res.devices || [];
            inputs = [];
            for (var i = 0; i < list.length; i++) {
                if (/^HDMI/i.test(list[i].id || '')) inputs.push(list[i]);
            }
            finish();
        });
    }

    function isDefault(app) {
        for (var i = 0; i < DEFAULTS.length; i++) {
            if (DEFAULTS[i].test(app.key)) return true;
        }
        return false;
    }

    function defaultRank(app) {
        for (var i = 0; i < DEFAULTS.length; i++) if (DEFAULTS[i].test(app.key)) return i;
        return DEFAULTS.length;
    }

    /**
     * callback(home, all, inputs): the home list (shown apps in saved order, then Settings and
     * Customize), every app in saved order with its shown flag for the Customize screen, and the inputs.
     * When the TV lists no apps (it does while still starting up), the home list has only Settings
     * and Customize, ok is false, and the saved order is left alone so the caller can try again.
     */
    function load(callback) {
        loadAll(function (all, settings, inputs) {
            if (!all.length) { callback(builtIns([], settings), [], inputs, false); return; }
            var p = prefs(), firstRun = !p, byKey = {}, i;
            if (!p) p = { order: [], shown: {} };
            for (i = 0; i < all.length; i++) byKey[all[i].key] = all[i];

            // Apps missing from this listing stay in the saved order, so an app that is updating, or
            // one the TV hasn't listed yet, comes back in its place.
            var known = {}, order = p.order.slice();
            for (i = 0; i < order.length; i++) known[order[i]] = true;
            var fresh = [];
            for (i = 0; i < all.length; i++) if (!known[all[i].key]) fresh.push(all[i]);
            fresh.sort(function (a, b) {
                var r = defaultRank(a) - defaultRank(b);
                return r || (a.title.toLowerCase() < b.title.toLowerCase() ? -1 : 1);
            });
            for (i = 0; i < fresh.length; i++) {
                order.push(fresh[i].key);
                if (firstRun && isDefault(fresh[i])) p.shown[fresh[i].key] = true;
            }
            if (firstRun) {
                var any = false;
                for (i = 0; i < order.length; i++) any = any || !!p.shown[order[i]];
                for (i = 0; !any && i < Math.min(6, order.length); i++) p.shown[order[i]] = true;
            }
            if (firstRun || fresh.length) {
                p.order = order;
                savePrefs(p);
            }

            var home = [], list = [];
            for (i = 0; i < order.length; i++) {
                var app = byKey[order[i]];
                if (!app) continue;
                app.shown = !!p.shown[app.key];
                list.push(app);
                if (app.shown) home.push(app);
            }
            callback(builtIns(home, settings), list, inputs, true);
        });
    }

    function builtIns(home, settings) {
        home.push({ key: 'settings', id: SETTINGS, title: 'Settings', params: null,
            icon: settings ? fileUrl(settings.icon) : null, special: true });
        home.push({ key: 'customize', id: SELF, title: 'Customize', special: true });
        return home;
    }

    /** Saves the Customize screen's order and visibility, keeping apps it didn't list at the end. */
    function save(list) {
        var old = prefs() || { order: [], shown: {} };
        var p = { order: [], shown: {} }, listed = {}, i;
        for (i = 0; i < list.length; i++) {
            p.order.push(list[i].key);
            listed[list[i].key] = true;
            if (list[i].shown) p.shown[list[i].key] = true;
        }
        for (i = 0; i < old.order.length; i++) {
            var key = old.order[i];
            if (listed[key]) continue;
            p.order.push(key);
            if (old.shown[key]) p.shown[key] = true;
        }
        savePrefs(p);
    }

    function launch(app, callback) {
        var params = { id: app.id };
        if (app.params) params.params = app.params;
        Luna.call('luna://com.webos.applicationManager/launch', params, function (res) {
            callback(!!(res && res.returnValue), res);
        });
    }

    return { load: load, save: save, launch: launch };
})();
