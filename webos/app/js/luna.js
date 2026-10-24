/*
 * Luna bus calls from a web app, without webOSTV.js. Each call keeps its own PalmServiceBridge alive
 * until the reply arrives (or for good, when subscribed), or the bridge is garbage collected mid-call.
 * Outside a TV (a desktop browser) every call answers from Luna.mock, so the UI can be previewed.
 */
var Luna = (function () {
    var live = {};
    var nextId = 1;
    var onTv = typeof window.PalmServiceBridge !== 'undefined';

    function call(uri, params, callback) {
        params = params || {};
        callback = callback || function () {};
        if (!onTv) {
            setTimeout(function () { callback(Luna.mock(uri, params)); }, 30);
            return;
        }
        var id = nextId++;
        var bridge = new window.PalmServiceBridge();
        live[id] = bridge;
        bridge.onservicecallback = function (msg) {
            var res;
            try { res = JSON.parse(msg); } catch (e) { res = { returnValue: false, errorText: 'bad reply' }; }
            if (!params.subscribe) delete live[id];
            callback(res);
        };
        bridge.call(uri, JSON.stringify(params));
    }

    /** Runs a shell command as root through Homebrew Channel. callback(stdout or null on failure). */
    function exec(command, callback) {
        call('luna://org.webosbrew.hbchannel.service/exec', { command: command }, function (res) {
            callback(res && res.returnValue ? res.stdoutString || '' : null, res);
        });
    }

    /** Calls a Luna method with root rights (for methods a third-party app may not call directly). */
    function rootCall(uri, params, callback) {
        var json = JSON.stringify(params || {}).replace(/'/g, "'\\''");
        exec("luna-send -n 1 '" + uri + "' '" + json + "'", function (out) {
            var res = null;
            if (out) {
                try { res = JSON.parse(out); } catch (e) { res = null; }
            }
            callback(res);
        });
    }

    return { call: call, exec: exec, rootCall: rootCall, onTv: onTv, mock: function () { return { returnValue: false }; } };
})();
