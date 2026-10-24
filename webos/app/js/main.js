/* Marquee's home screen and Customize screen for webOS. Ported from MainActivity and SettingsActivity. */
(function () {
    // Per distance from the selection: vertical offset, scale relative to the 60px name, opacity.
    var ROW_Y = [0, 86, 136, 178, 214];
    var ROW_SCALE = [1, 0.46, 0.38, 0.32, 0.28];
    var ROW_ALPHA = [1, 0.55, 0.26, 0, 0];
    var RAIL = 136;
    var HOLD_MS = 600;
    var KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, OK: 13, BACK: 461, ESC: 27, BACKSPACE: 8,
        RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406 };

    var $ = function (id) { return document.getElementById(id); };
    var stage = $('stage'), namesEl = $('names'), thumb = $('railThumb');
    var back = $('backBack'), front = $('backFront');

    var items = [], all = [], index = 0;
    var inputs = [], iIndex = 0, zone = 'apps'; // zone: 'apps' or 'inputs', the column Up/Down/OK act on
    var nameViews = {};      // key -> element, reused across reloads
    var colors = {};         // key -> [main, accent]
    var shownKey = null;     // whose backdrop is on screen
    var okDownAt = 0, holdTimer = null, holdFired = false;
    var customizing = false;

    // ---- Layout --------------------------------------------------------------------------

    function fit() {
        var z = window.innerWidth / 960;
        stage.style.zoom = z;
        if (!('zoom' in stage.style)) {
            stage.style.transformOrigin = '0 0';
            stage.style.transform = 'scale(' + z + ')';
        }
    }

    function offsetOf(i) {
        var n = items.length, d = ((i - index) % n + n) % n;
        return d > n / 2 ? d - n : d;
    }

    function stateCss(el, offset) {
        var a = Math.min(Math.abs(offset), ROW_Y.length - 1);
        var y = (offset > 0 ? 1 : offset < 0 ? -1 : 0) * ROW_Y[a] - 10;
        // The name's box is centred on the row line: top is 270, so lift it by half its 60px height.
        el.style.transform = 'translate3d(0,' + (y - 30) + 'px,0) scale(' + ROW_SCALE[a] + ')';
        el.style.opacity = ROW_ALPHA[a];
    }

    function layoutNames(animate) {
        for (var i = 0; i < items.length; i++) {
            var el = nameViews[items[i].key];
            var offset = offsetOf(i);
            var prev = el.marqueeOffset === undefined ? offset : el.marqueeOffset;
            el.marqueeOffset = offset;
            if (!animate) {
                el.className = 'name';
                stateCss(el, offset);
                continue;
            }
            if (Math.abs(offset - prev) > 1) {
                // Wrapped around the end of the list: fade in from just beyond its new place.
                el.className = 'name';
                stateCss(el, offset + (offset >= 0 ? 1 : -1));
                void el.offsetWidth;
            }
            el.className = 'name anim';
            stateCss(el, offset);
        }
        moveRail();
    }

    function moveRail() {
        var n = items.length;
        if (!n) return;
        var h = Math.max(14, Math.round(RAIL / n));
        var y = n === 1 ? 0 : -RAIL / 2 + h / 2 + index * (RAIL - h) / (n - 1);
        thumb.style.height = h + 'px';
        thumb.style.marginTop = (-h / 2) + 'px';
        thumb.style.transform = 'translate3d(0,' + y + 'px,0)';
    }

    // ---- Backdrop ------------------------------------------------------------------------

    function copyFrontToBack() {
        back.getContext('2d').drawImage(front, 0, 0);
        front.className = '';
        front.style.opacity = 0;
    }

    function current() {
        return zone === 'inputs' ? inputs[iIndex] : items[index];
    }

    function showBackdrop(animate) {
        var app = current();
        if (!app) return;
        var target = colors[app.key] ? app.key : '_default';
        if (target === shownKey) return;
        if (parseFloat(getComputedStyle(front).opacity) >= 0.5) copyFrontToBack();
        shownKey = target;
        var c = colors[target] || Colors.DEFAULT;
        if (!animate) {
            Colors.paintBackdrop(back, c);
            front.className = '';
            front.style.opacity = 0;
            return;
        }
        front.className = '';
        front.style.opacity = 0;
        Colors.paintBackdrop(front, c);
        void front.offsetWidth;
        front.className = 'fading';
        front.style.opacity = 1;
    }

    front.addEventListener('transitionend', function () {
        if (front.style.opacity === '1') copyFrontToBack();
    });

    function prepareColors(list) {
        var queue = list.slice(0);
        // The selected app first, so its backdrop appears as soon as possible.
        var first = current();
        queue.sort(function (a, b) { return (a === first ? -1 : 0) - (b === first ? -1 : 0); });
        (function next() {
            var app = queue.shift();
            if (!app) return;
            if (colors[app.key] || app.key === 'customize') { next(); return; }
            Colors.forApp(app, function (c) {
                colors[app.key] = c;
                if (current() === app) showBackdrop(true);
                setTimeout(next, 0);
            });
        })();
    }

    // ---- Data ----------------------------------------------------------------------------

    var retryTimer = null, retries = 0;

    function reload() {
        var selected = items.length ? items[index].key : null;
        var selectedInput = inputs.length ? inputs[iIndex].key : null;
        clearTimeout(retryTimer);
        Apps.load(function (home, list, hdmi, ok) {
            if (!ok) {
                // The TV listed no apps (it is still starting up). Ask again shortly, and keep the
                // apps already on screen meanwhile.
                if (retries < 20) retryTimer = setTimeout(reload, Math.min(500 * ++retries, 3000));
                if (items.length) return;
            } else {
                // Coming back from an empty listing: start on the first app, not on Settings.
                if (retries) selected = null;
                retries = 0;
            }
            items = home;
            all = list;
            inputs = hdmi;
            iIndex = 0;
            for (var k = 0; k < inputs.length; k++) if (inputs[k].key === selectedInput) iIndex = k;
            if (!inputs.length) zone = 'apps';
            renderInputs();
            index = 0;
            var current = {}, i;
            for (i = 0; i < items.length; i++) {
                current[items[i].key] = true;
                if (items[i].key === selected) index = i;
            }
            for (var key in nameViews) {
                if (nameViews.hasOwnProperty(key) && !current[key]) {
                    namesEl.removeChild(nameViews[key]);
                    delete nameViews[key];
                }
            }
            for (i = 0; i < items.length; i++) {
                var el = nameViews[items[i].key];
                if (!el) {
                    el = document.createElement('div');
                    el.className = 'name';
                    el.style.opacity = 0;
                    nameViews[items[i].key] = el;
                    namesEl.appendChild(el);
                }
                if (el.textContent !== items[i].title) el.textContent = items[i].title;
            }
            colors.customize = colors.customize || [[0x4A, 0x3A, 0x70], [0x2B, 0x35, 0x50]];
            layoutNames(false);
            showBackdrop(false);
            prepareColors(items.concat(inputs, all));
        });
    }

    function select(i) {
        var n = items.length;
        if (!n) return;
        index = ((i % n) + n) % n;
        layoutNames(true);
        showBackdrop(true);
    }

    // ---- Inputs column -------------------------------------------------------------------

    function renderInputs() {
        var box = $('inputs'), list = $('inputList');
        list.innerHTML = '';
        for (var i = 0; i < inputs.length; i++) {
            var el = document.createElement('div');
            el.className = 'input' + (i === iIndex ? ' sel' : '');
            el.textContent = inputs[i].title;
            list.appendChild(el);
        }
        box.className = !inputs.length ? 'hidden' : zone === 'inputs' ? 'active' : '';
        $('inputsHint').className = inputs.length ? '' : 'hidden';
        namesEl.className = zone === 'inputs' ? 'away' : '';
    }

    function setZone(z) {
        if (z === zone || (z === 'inputs' && !inputs.length)) return;
        zone = z;
        renderInputs();
        showBackdrop(true);
    }

    function selectInput(i) {
        var n = inputs.length;
        if (!n) return;
        iIndex = ((i % n) + n) % n;
        renderInputs();
        showBackdrop(true);
    }

    function launchSelected() {
        var app = current();
        if (!app) return;
        if (app.key === 'customize') { openCustomize(); return; }
        Apps.launch(app, function (ok, res) {
            if (!ok) toast("Can't open " + app.title + (res && res.errorText ? ' (' + res.errorText + ')' : ''));
        });
    }

    var toastTimer = null;
    function toast(text) {
        var t = $('toast');
        t.textContent = text;
        t.className = '';
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { t.className = 'hidden'; }, 2500);
    }

    // ---- Status: clock and network -------------------------------------------------------

    function pad(n) { return (n < 10 ? '0' : '') + n; }

    function updateClock() {
        var d = new Date();
        $('clock').textContent = d.getHours() + ':' + pad(d.getMinutes());
        var text;
        try {
            text = d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
        } catch (e) {
            text = d.toDateString().slice(0, 10);
        }
        $('date').textContent = text;
        setTimeout(updateClock, 60000 - d.getSeconds() * 1000 - d.getMilliseconds() + 50);
    }

    function wifiSvg(level, wired) {
        var on = 'rgba(255,255,255,0.9)', off = 'rgba(255,255,255,0.25)', s = '';
        if (wired) {
            return '<g fill="none" stroke="' + on + '" stroke-width="1.4"><rect x="6" y="1" width="4" height="3.4"/>' +
                '<path d="M8 4.4V7M3.2 7h9.6M3.2 7v2.4M12.8 7v2.4"/><rect x="1.2" y="9.4" width="4" height="3"/>' +
                '<rect x="10.8" y="9.4" width="4" height="3"/></g>';
        }
        s += '<circle cx="8" cy="11.4" r="1.3" fill="' + (level >= 1 ? on : off) + '"/>';
        for (var i = 1; i <= 3; i++) {
            var r = 3.4 * i, a = Math.SQRT1_2 * r;
            s += '<path d="M' + (8 - a) + ' ' + (11.6 - a) + 'A' + r + ' ' + r + ' 0 0 1 ' + (8 + a) + ' ' + (11.6 - a) +
                '" fill="none" stroke-linecap="round" stroke-width="1.4" stroke="' + (level > i ? on : off) + '"/>';
        }
        if (level < 0) s += '<path d="M2 1L14 12.5" stroke="' + on + '" stroke-width="1.4"/>';
        return s;
    }

    function renderNet(text, level, wired) {
        $('net').textContent = text;
        $('wifi').innerHTML = wifiSvg(level, wired);
    }

    // Asks the Wi-Fi chip for band, link speed and signal, the same details Marquee shows on Android.
    var LINK_CMD = 'for i in $(ls /sys/class/net); do iw dev $i link 2>/dev/null; done; cat /proc/net/wireless 2>/dev/null';

    function updateNetwork() {
        Luna.call('luna://com.webos.service.connectionmanager/getStatus', {}, function (res) {
            var wired = res.wired && res.wired.state === 'connected';
            var wifi = res.wifi && res.wifi.state === 'connected';
            if (wired) { renderNet('Ethernet', 4, true); return; }
            if (!wifi) { renderNet(res.returnValue ? 'Offline' : '', -1, false); return; }
            var ssid = res.wifi.ssid || 'Wi-Fi';
            renderNet(ssid, 4, false);
            Luna.exec(LINK_CMD, function (out) {
                if (!out) return;
                var freq = /freq:\s*(\d+)/.exec(out), rate = /tx bitrate:\s*([\d.]+)\s*MBit/i.exec(out);
                var sig = /signal:\s*(-\d+)\s*dBm/.exec(out);
                var parts = [];
                if (freq) parts.push(+freq[1] >= 5900 ? '6 GHz' : +freq[1] >= 4900 ? '5 GHz' : '2.4 GHz');
                if (rate) parts.push(Math.round(+rate[1]) + ' Mbps');
                var level = 4;
                if (sig) {
                    var dbm = +sig[1];
                    level = dbm >= -55 ? 4 : dbm >= -67 ? 3 : dbm >= -78 ? 2 : 1;
                } else {
                    var q = /wlan\d+:\s+\S+\s+(\d+)\./.exec(out);
                    if (q) level = +q[1] >= 50 ? 4 : +q[1] >= 35 ? 3 : +q[1] >= 20 ? 2 : 1;
                }
                renderNet(parts.length ? parts.join(' · ') : ssid, level, false);
            });
        });
    }

    // ---- Customize -----------------------------------------------------------------------

    // Switches for the background helper (/var/lib/webosbrew/init.d/marquee-autostart), which reads
    // this file on every event. Written as root through Homebrew Channel; hidden if that isn't there.
    var CONF = '/var/lib/webosbrew/marquee.conf';
    var behaviour = [
        { key: 'MARQUEE_HOME', label: 'Home button opens Marquee', on: true },
        { key: 'MARQUEE_POWERON', label: 'Open Marquee when the TV turns on', on: true }
    ];
    var behaviourReady = false;

    // Kept in localStorage too, so a Home press can be passed on without waiting for the file.
    function homeSwitchOff() {
        try { return localStorage.getItem('marquee.homeOff') === '1'; } catch (e) { return false; }
    }

    function rememberHomeSwitch() {
        try { localStorage.setItem('marquee.homeOff', behaviour[0].on ? '0' : '1'); } catch (e) { /* ignore */ }
    }

    function loadBehaviour() {
        Luna.exec('cat ' + CONF + ' 2>/dev/null; echo', function (out) {
            if (out === null) return;
            behaviourReady = true;
            for (var i = 0; i < behaviour.length; i++) {
                behaviour[i].on = !new RegExp('^' + behaviour[i].key + '=0', 'm').test(out);
            }
            rememberHomeSwitch();
            if (customizing) renderCustomize();
        });
    }

    function saveBehaviour() {
        var text = '';
        for (var i = 0; i < behaviour.length; i++) text += behaviour[i].key + '=' + (behaviour[i].on ? 1 : 0) + '\\n';
        rememberHomeSwitch();
        Luna.exec("printf '" + text + "' > " + CONF, function (out) {
            if (out === null) toast("Couldn't save the setting");
        });
    }

    var cIndex = 0, cCol = 0, rows = [];

    /** Number of behaviour rows above the apps in the Customize list. */
    function nb() {
        return behaviourReady ? behaviour.length : 0;
    }

    function openCustomize() {
        customizing = true;
        cIndex = 0;
        cCol = 0;
        renderCustomize();
        $('customize').className = '';
        loadBehaviour();
    }

    function closeCustomize() {
        customizing = false;
        $('customize').className = 'hidden';
        Apps.save(all);
        reload();
    }

    function section(parent, text) {
        var h = document.createElement('div');
        h.className = 'section';
        h.textContent = text;
        parent.appendChild(h);
    }

    function renderCustomize() {
        var list = $('list');
        list.innerHTML = '';
        var inner = document.createElement('div');
        inner.id = 'listInner';
        rows = [];
        var i, row;
        if (behaviourReady) {
            section(inner, 'Behaviour');
            for (i = 0; i < behaviour.length; i++) {
                row = document.createElement('div');
                row.className = 'row ' + (behaviour[i].on ? 'on' : 'off');
                row.innerHTML = '<span class="label"></span><span class="state"></span>';
                row.children[0].textContent = behaviour[i].label;
                row.children[1].textContent = behaviour[i].on ? 'On' : 'Off';
                inner.appendChild(row);
                rows.push(row);
            }
            section(inner, 'Apps');
        }
        for (i = 0; i < all.length; i++) {
            var app = all[i], c = colors[app.key];
            row = document.createElement('div');
            row.className = 'row ' + (app.shown ? 'on' : 'off');
            row.innerHTML = '<span class="swatch"></span><span class="label"></span><span class="state"></span>' +
                '<span class="btn up">▲</span><span class="btn down">▼</span>';
            row.children[0].style.background = c ? Colors.css(c[0]) : '#2B3550';
            row.children[1].textContent = app.title;
            row.children[2].textContent = app.shown ? 'Shown' : 'Hidden';
            inner.appendChild(row);
            rows.push(row);
        }
        list.appendChild(inner);
        focusRow();
    }

    function focusRow() {
        if (cIndex < nb()) cCol = 0;
        for (var i = 0; i < rows.length; i++) rows[i].className = rows[i].className.replace(/\s*focus.*$/, '');
        var row = rows[cIndex];
        if (!row) return;
        row.className += ' focus col' + cCol;
        // Keep the focused row in the middle third of the list.
        var listH = $('list').offsetHeight, inner = $('listInner');
        var scroll = Math.max(0, Math.min(row.offsetTop - listH / 3, inner.offsetHeight - listH + 48));
        inner.style.transform = 'translate3d(0,' + (-scroll) + 'px,0)';
    }

    function move(delta) {
        var a = cIndex - nb(), j = a + delta;
        if (a < 0 || j < 0 || j >= all.length) return;
        var t = all[a];
        all[a] = all[j];
        all[j] = t;
        cIndex += delta;
        renderCustomize();
    }

    function customizeKey(k) {
        var isApp = cIndex >= nb();
        if (k === KEY.UP && cIndex > 0) { cIndex--; focusRow(); }
        else if (k === KEY.DOWN && cIndex < rows.length - 1) { cIndex++; focusRow(); }
        else if (k === KEY.RIGHT && isApp && cCol < 2) { cCol++; focusRow(); }
        else if (k === KEY.LEFT && cCol > 0) { cCol--; focusRow(); }
        else if (k === KEY.OK) {
            if (!isApp) {
                behaviour[cIndex].on = !behaviour[cIndex].on;
                saveBehaviour();
                renderCustomize();
            } else if (cCol === 0) {
                all[cIndex - nb()].shown = !all[cIndex - nb()].shown;
                renderCustomize();
            } else {
                move(cCol === 1 ? -1 : 1);
            }
        } else if (k === KEY.BACK || k === KEY.ESC || k === KEY.BACKSPACE) {
            closeCustomize();
        }
    }

    // ---- Keys ----------------------------------------------------------------------------

    document.addEventListener('keydown', function (e) {
        var k = e.keyCode;
        e.preventDefault();
        if (customizing) {
            if (k === KEY.OK && okDownAt) return; // the OK that opened Customize is still held
            customizeKey(k);
            return;
        }
        if (zone === 'inputs') {
            if (k === KEY.UP) selectInput(iIndex - 1);
            else if (k === KEY.DOWN) selectInput(iIndex + 1);
            else if (k === KEY.LEFT || k === KEY.BACK) setZone('apps');
            else if (k === KEY.OK && !e.repeat) launchSelected();
            return;
        }
        if (k === KEY.UP) select(index - 1);
        else if (k === KEY.DOWN) select(index + 1);
        else if (k === KEY.RIGHT) setZone('inputs');
        else if (k === KEY.OK) {
            if (okDownAt) return; // key repeat
            okDownAt = Date.now();
            holdFired = false;
            holdTimer = setTimeout(function () { holdFired = true; openCustomize(); }, HOLD_MS);
        } else if (k === KEY.RED || k === KEY.BLUE) {
            openCustomize();
        }
    });

    document.addEventListener('keyup', function (e) {
        if (e.keyCode !== KEY.OK || !okDownAt) return;
        e.preventDefault();
        clearTimeout(holdTimer);
        okDownAt = 0;
        if (!holdFired && !customizing) launchSelected();
    });

    function launchParams() {
        try {
            var p = window.PalmSystem && window.PalmSystem.launchParams;
            return (typeof p === 'string' ? JSON.parse(p || '{}') : p) || {};
        } catch (e) {
            return {};
        }
    }

    // The Home button reaches Marquee with fromHome when the TV's compositor has Marquee's change. With
    // the "Home button opens Marquee" switch off, Marquee passes it on to LG's home without showing.
    function passHomeOn() {
        if (!launchParams().fromHome || !homeSwitchOff()) return false;
        Luna.call('luna://com.webos.applicationManager/launch', { id: 'com.webos.app.home' });
        return true;
    }

    // Marquee launched again while it was running in the background (from LG's home, the Home button
    // or the power-on script). With handlesRelaunch the TV leaves the window hidden until the app
    // asks to come forward. Then jump back to the first app.
    document.addEventListener('webOSRelaunch', function () {
        if (passHomeOn()) return;
        if (window.PalmSystem && window.PalmSystem.activate) window.PalmSystem.activate();
        if (customizing) closeCustomize();
        setZone('apps');
        if (index !== 0) select(0);
    });

    document.addEventListener('visibilitychange', function () {
        if (!document.hidden && !customizing) { reload(); updateNetwork(); }
    });

    window.addEventListener('resize', fit);

    passHomeOn();
    loadBehaviour();
    fit();
    Colors.paintBackdrop(back, Colors.DEFAULT);
    shownKey = '_default';
    updateClock();
    updateNetwork();
    setInterval(function () { if (!document.hidden) updateNetwork(); }, 15000);
    reload();
})();
