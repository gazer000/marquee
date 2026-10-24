/*
 * App colours and backdrops, ported from Marquee's AppStore and Backdrop. Colours come from the app's
 * icon when the TV lets us read its pixels, then from the launch point's own colour, then from a small
 * table of well-known brands, and last from a hue picked from the app id so every app still differs.
 */
var Colors = (function () {
    var DEFAULT_COLOR = [0x2B, 0x35, 0x50];
    var BACKDROP_W = 480, BACKDROP_H = 270;

    // [main, accent] for apps whose icons are mostly white or text, or that we can't read.
    var BRANDS = [
        [/youtube\.leanback|^youtube/i, '#E62117', '#7A1010'],
        [/netflix/i, '#E50914', '#5A0A0F'],
        [/amazon|primevideo/i, '#00A8E1', '#1A3D7C'],
        [/disney/i, '#113CCF', '#01A3C7'],
        [/spotify/i, '#1DB954', '#0F5A34'],
        [/limelight|moonlight|artemis/i, '#5B3FD0', '#1D8FE0'],
        [/plex/i, '#E5A00D', '#5A3D05'],
        [/apple|appletv/i, '#3A3A44', '#1C6DD0'],
        [/twitch/i, '#9146FF', '#3A1C70'],
        [/hbo/i, '#2F1BD0', '#7A2FE0'],
        [/stremio/i, '#7B5BF5', '#2E1B70'],
        [/kodi/i, '#17B2E7', '#0E4F80'],
        [/ps5|ps4|playstation/i, '#0070D1', '#1B2A6B'],
        [/switch|nintendo/i, '#E60012', '#5A1020'],
        [/xbox/i, '#107C10', '#0B4D0B'],
        [/chromecast|google/i, '#4285F4', '#34A853'],
        [/hdmi|inputcommon|livetv|tvguide/i, '#2F5B8C', '#1E8C7A'],
        [/settings/i, '#3A4E66', '#4A3A70']
    ];

    function hex(s) {
        return [parseInt(s.substr(1, 2), 16), parseInt(s.substr(3, 2), 16), parseInt(s.substr(5, 2), 16)];
    }

    function rgbToHsv(r, g, b) {
        r /= 255; g /= 255; b /= 255;
        var max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min, h = 0;
        if (d) {
            if (max === r) h = ((g - b) / d) % 6;
            else if (max === g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        return [h, max ? d / max : 0, max];
    }

    function hsvToRgb(h, s, v) {
        var c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c, r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; } else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return [Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)];
    }

    function shiftHue(rgb, deg) {
        var hsv = rgbToHsv(rgb[0], rgb[1], rgb[2]);
        return hsvToRgb((hsv[0] + deg + 360) % 360, hsv[1], hsv[2]);
    }

    /** Keeps the hue but makes it deep enough that white text stays readable on top. */
    function darken(rgb) {
        var hsv = rgbToHsv(rgb[0], rgb[1], rgb[2]);
        if (hsv[1] < 0.15) return DEFAULT_COLOR;
        return hsvToRgb(hsv[0], Math.max(0.55, Math.min(hsv[1], 0.9)), Math.min(hsv[2], 0.62));
    }

    function isGray(rgb) {
        return rgbToHsv(rgb[0], rgb[1], rgb[2])[1] < 0.2;
    }

    /** {main, accent}: the most prominent saturated hue, and a second one at least 45 degrees away. */
    function themeColors(data) {
        var bins = 24, weight = [], sum = [], i, opaque = 0, avg = [0, 0, 0], n = data.length / 4;
        for (i = 0; i < bins; i++) { weight[i] = 0; sum[i] = [0, 0, 0]; }
        for (i = 0; i < data.length; i += 4) {
            if (data[i + 3] < 128) continue;
            var r = data[i], g = data[i + 1], b = data[i + 2];
            avg[0] += r; avg[1] += g; avg[2] += b; opaque++;
            var hsv = rgbToHsv(r, g, b);
            if (hsv[2] < 0.15 || hsv[1] < 0.25) continue;
            var w = hsv[1] * hsv[1] * hsv[2];
            var bin = Math.floor(hsv[0] / (360 / bins)) % bins;
            weight[bin] += w;
            sum[bin][0] += r * w; sum[bin][1] += g * w; sum[bin][2] += b * w;
        }
        var best = -1, bestScore = 0;
        for (i = 0; i < bins; i++) {
            var score = weight[i] + 0.5 * (weight[(i + 1) % bins] + weight[(i + bins - 1) % bins]);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        function binColor(k) {
            return [Math.round(sum[k][0] / weight[k]), Math.round(sum[k][1] / weight[k]), Math.round(sum[k][2] / weight[k])];
        }
        if (best >= 0 && weight[best] > n * 0.01) {
            var second = -1, secondWeight = 0;
            for (i = 0; i < bins; i++) {
                var d = Math.abs(i - best);
                if (Math.min(d, bins - d) < 3) continue;
                if (weight[i] > secondWeight) { secondWeight = weight[i]; second = i; }
            }
            return { main: binColor(best), accent: second >= 0 && secondWeight > weight[best] * 0.15 ? binColor(second) : null };
        }
        if (!opaque) return null;
        return { main: [avg[0] / opaque | 0, avg[1] / opaque | 0, avg[2] / opaque | 0], accent: null };
    }

    /** Reads an icon's pixels. Fails quietly when the TV treats the file as cross-origin. */
    function fromIcon(url, callback) {
        if (!url) { callback(null); return; }
        var img = new Image();
        img.onload = function () {
            try {
                var c = document.createElement('canvas');
                c.width = 48; c.height = 48;
                var g = c.getContext('2d');
                g.drawImage(img, 0, 0, 48, 48);
                callback(themeColors(g.getImageData(0, 0, 48, 48).data));
            } catch (e) {
                callback(null);
            }
        };
        img.onerror = function () { callback(null); };
        img.src = url;
    }

    function fromBrand(id, title) {
        for (var i = 0; i < BRANDS.length; i++) {
            if (BRANDS[i][0].test(id) || BRANDS[i][0].test(title || '')) return { main: hex(BRANDS[i][1]), accent: hex(BRANDS[i][2]) };
        }
        return null;
    }

    function fromId(id) {
        var h = 0;
        for (var i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
        var main = hsvToRgb(h % 360, 0.7, 0.55);
        return { main: main, accent: shiftHue(main, 40) };
    }

    /** callback([main, accent]) for an app, cached by id and icon path. */
    function forApp(app, callback) {
        var key = 'marquee.colors:' + app.id + ':' + (app.icon || '');
        try {
            var cached = JSON.parse(localStorage.getItem(key));
            if (cached && cached.length === 2) { callback(cached); return; }
        } catch (e) { /* recompute */ }
        function done(found, cache) {
            var main = darken(found.main);
            var accent = found.accent ? darken(found.accent) : shiftHue(main, 35);
            var result = [main, accent];
            if (cache) {
                try { localStorage.setItem(key, JSON.stringify(result)); } catch (e) { /* full */ }
            }
            callback(result);
        }
        var brand = fromBrand(app.id, app.title);
        if (brand) { done(brand, true); return; }
        fromIcon(app.icon, function (found) {
            if (found && !isGray(found.main)) { done(found, true); return; }
            if (app.iconColor && /^#[0-9a-f]{6}$/i.test(app.iconColor) && !isGray(hex(app.iconColor))) {
                done({ main: hex(app.iconColor), accent: null }, true);
                return;
            }
            done(fromId(app.id), !!found);
        });
    }

    function rgba(rgb, a) {
        return 'rgba(' + rgb[0] + ',' + rgb[1] + ',' + rgb[2] + ',' + a + ')';
    }

    function glow(g, cx, cy, r, rgb, strength) {
        var grad = g.createRadialGradient(cx, cy, 0, cx, cy, r);
        grad.addColorStop(0, rgba(rgb, strength));
        grad.addColorStop(0.45, rgba(rgb, strength * 0.45));
        grad.addColorStop(1, rgba(rgb, 0));
        g.fillStyle = grad;
        g.fillRect(0, 0, BACKDROP_W, BACKDROP_H);
    }

    /** Paints Marquee's backdrop into a 480x270 canvas: base, two glows, and the text scrims. */
    function paintBackdrop(canvas, colors) {
        var g = canvas.getContext('2d'), w = BACKDROP_W, h = BACKDROP_H, main = colors[0], accent = colors[1];
        g.globalCompositeOperation = 'source-over';
        g.fillStyle = rgba([Math.round(main[0] * 0.3), Math.round(main[1] * 0.3), Math.round(main[2] * 0.3)], 1);
        g.fillRect(0, 0, w, h);
        glow(g, w * 0.82, h * 0.28, w * 0.80, main, 0.95);
        glow(g, w * 0.08, h * 1.08, w * 0.62, accent, 0.70);
        var s = g.createLinearGradient(0, 0, w * 0.75, 0);
        s.addColorStop(0, 'rgba(0,0,0,0.72)');
        s.addColorStop(0.55, 'rgba(0,0,0,0.33)');
        s.addColorStop(1, 'rgba(0,0,0,0)');
        g.fillStyle = s; g.fillRect(0, 0, w, h);
        s = g.createLinearGradient(0, 0, 0, h * 0.35);
        s.addColorStop(0, 'rgba(0,0,0,0.4)');
        s.addColorStop(1, 'rgba(0,0,0,0)');
        g.fillStyle = s; g.fillRect(0, 0, w, h);
        s = g.createLinearGradient(0, h * 0.75, 0, h);
        s.addColorStop(0, 'rgba(0,0,0,0)');
        s.addColorStop(1, 'rgba(0,0,0,0.35)');
        g.fillStyle = s; g.fillRect(0, 0, w, h);
    }

    return {
        DEFAULT: [DEFAULT_COLOR, [0x3A, 0x2F, 0x55]],
        forApp: forApp,
        paintBackdrop: paintBackdrop,
        css: function (rgb) { return rgba(rgb, 1); },
        themeColors: themeColors
    };
})();
