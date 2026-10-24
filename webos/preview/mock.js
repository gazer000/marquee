Luna.mock = function (uri, params) {
  if (/listLaunchPoints/.test(uri)) return { returnValue: true, launchPoints: [
    { id: 'youtube.leanback.v4', title: 'YouTube', icon: '' },
    { id: 'netflix', title: 'Netflix', icon: '' },
    { id: 'com.limelight.webos', title: 'Moonlight', icon: '' },
    { id: 'amazon', title: 'Prime Video', icon: '' },
    { id: 'com.disney.disneyplus-prod', title: 'Disney+', icon: '' },
    { id: 'spotify-beehive', title: 'Spotify', icon: '' },
    { id: 'com.palm.app.settings', title: 'Settings', icon: '' },
    { id: 'com.webos.app.browser', title: 'Web Browser', icon: '', iconColor: '#1f6fd0' },
    { id: 'com.webos.app.hdmi1', title: 'HDMI 1' },
    { id: 'org.webosbrew.hbchannel', title: 'Homebrew Channel', iconColor: '#5c2d91' }]};
  if (/getAllInputStatus/.test(uri)) return { returnValue: true, devices: [
    { id: 'HDMI_1', label: 'PS5 Bảng điều khiển trò chơi', appId: 'com.webos.app.hdmi1', connected: true, hdmiPlugIn: true, subList: [{ labelName: 'PS5' }] },
    { id: 'HDMI_2', label: 'Switch2 Bảng điều khiển trò chơi', appId: 'com.webos.app.hdmi2', connected: true, hdmiPlugIn: true, subList: [{ labelName: 'Switch2' }] },
    { id: 'HDMI_3', label: 'Chromecast', appId: 'com.webos.app.hdmi3', connected: true, hdmiPlugIn: true, subList: [{ osdName: 'Chromecast' }] },
    { id: 'HDMI_4', label: 'HDMI 4', appId: 'com.webos.app.hdmi4', connected: false, hdmiPlugIn: false, subList: [] }]};
  if (/connectionmanager/.test(uri)) return { returnValue: true, wifi: { state: 'connected', ssid: 'R1.05 P2811 5G' }, wired: { state: 'disconnected' } };
  if (/hbchannel.service\/exec/.test(uri)) return { returnValue: true, stdoutString: 'Connected to aa:bb\n\tfreq: 5180\n\tsignal: -52 dBm\n\ttx bitrate: 866.7 MBit/s\n' };
  if (/launch$/.test(uri)) { window.__launched = params.id; return { returnValue: true }; }
  return { returnValue: false };
};
