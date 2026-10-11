/* App Store web — all app data, descriptions and icons load live from Google Drive. */
(function () {
  'use strict';

  var API_KEY = 'AIzaSyAX7T6Vd75LnhQg15IydOLEYqjfGUT8TO8';
  var ROOT_FOLDER = '1PBrhSIvDk0QrgNS6XeTeA1RDLFPeTqKV';
  var API = 'https://www.googleapis.com/drive/v3/files';

  var state = { apps: [], platform: 'Android', query: '' };

  function $(id) { return document.getElementById(id); }

  function toast(msg) {
    var t = $('toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(t._h);
    t._h = setTimeout(function () { t.hidden = true; }, 2600);
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function fmtSize(bytes) {
    if (!bytes || bytes <= 0) return '';
    var mb = bytes / (1024 * 1024);
    if (mb >= 1000) return (mb / 1024).toFixed(2) + ' GB';
    return (mb >= 10 ? Math.round(mb * 10) / 10 : Math.round(mb * 100) / 100) + ' MB';
  }

  function driveGet(params) {
    var qs = Object.keys(params).map(function (k) {
      return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    }).join('&');
    return fetch(API + '?' + qs).then(function (r) {
      if (!r.ok) throw new Error('Drive error ' + r.status);
      return r.json();
    });
  }

  function listAll(query) {
    var out = [];
    function page(token) {
      var p = {
        q: query,
        key: API_KEY,
        fields: 'files(id,name,mimeType,size,modifiedTime,thumbnailLink,iconLink),nextPageToken',
        pageSize: 1000
      };
      if (token) p.pageToken = token;
      return driveGet(p).then(function (d) {
        out = out.concat(d.files || []);
        if (d.nextPageToken) return page(d.nextPageToken);
        return out;
      });
    }
    return page(null);
  }

  function findSubfolder(parentId, name) {
    return driveGet({
      q: "'" + parentId + "' in parents and mimeType = 'application/vnd.google-apps.folder' and name = '" + name + "' and trashed = false",
      key: API_KEY,
      fields: 'files(id)'
    }).then(function (d) {
      return (d.files && d.files[0]) ? d.files[0].id : null;
    });
  }

  function stripExt(name, exts) {
    for (var i = 0; i < exts.length; i++) {
      if (name.toLowerCase().endsWith(exts[i])) return name.slice(0, -exts[i].length);
    }
    return name;
  }

  function parseFile(f, apiKey, iconMap, mdMap, platform) {
    var stripped = stripExt(f.name, ['.apk', '.zip']);
    var verName = '1.0', verCode = 1;

    // First _v match after the package name (mirrors the Android app)
    var vm = null, m, re = /_v([^_]+)/g;
    while ((m = re.exec(stripped)) !== null) {
      if (stripped.slice(0, m.index).indexOf('.') !== -1) { vm = m[1]; break; }
    }
    if (vm) {
      verName = vm;
      var code = parseInt(vm.replace(/\./g, ''), 10);
      if (!isNaN(code)) verCode = code;
    }
    var bm = stripped.match(/_(?:build|buid)_(\d+)/i);
    if (bm) verCode = parseInt(bm[1], 10) || verCode;

    var before = vm ? stripped.slice(0, stripped.indexOf('_v' + vm)).replace(/_+$/, '') : stripped;
    var key = before;
    if (!(mdMap[key] || iconMap[key])) {
      var cand = Object.keys(mdMap).concat(Object.keys(iconMap)).find(function (k) {
        return before.indexOf(k) === 0 && before.length > k.length && !/[A-Za-z0-9]/.test(before[k.length]);
      });
      if (cand) key = cand;
    }

    var segs = before.split('_'), pkgIdx = -1;
    for (var i = segs.length - 1; i >= 0; i--) {
      if (segs[i].indexOf('.') !== -1 && segs[i].indexOf(' ') === -1) { pkgIdx = i; break; }
    }
    var pkg = pkgIdx >= 0
      ? segs.slice(pkgIdx).join('.')
      : ('com.drive.app.' + segs.join('').toLowerCase().replace(/\+/g, 'plus').replace(/ /g, ''));
    var name = pkgIdx > 0 ? segs.slice(0, pkgIdx).join(' ').trim() : before.replace(/_/g, ' ').trim();

    var icon = iconMap[key] || iconMap[stripExt(f.name, ['.apk', '.zip'])] ||
      f.thumbnailLink || f.iconLink || '';
    var mdId = mdMap[key] || mdMap[stripExt(f.name, ['.apk', '.zip'])] || null;

    return {
      id: f.id, packageName: pkg, name: name || f.name,
      versionName: verName, versionCode: verCode,
      size: parseInt(f.size || '0', 10) || 0,
      fileName: f.name, mdId: mdId, iconUrl: icon,
      inlineDesc: '', platform: platform || 'Android',
      downloadUrl: 'https://drive.google.com/uc?export=download&id=' + f.id
    };
  }

  function parseFolder(folderId, platform) {
    return listAll("'" + folderId + "' in parents and trashed = false").then(function (files) {
      var isWin = platform === 'Windows';
      var apps = files.filter(function (f) {
        return isWin
          ? (f.mimeType === 'application/zip' || /\.zip$/i.test(f.name))
          : (f.mimeType === 'application/vnd.android.package-archive' || /\.apk$/i.test(f.name));
      });
      var iconMap = {}, mdMap = {};
      files.forEach(function (f) {
        if ((f.mimeType || '').indexOf('image/') === 0) {
          iconMap[f.name.replace(/\.(png|PNG|jpg|jpeg|webp)$/, '').trim()] =
            API + '/' + f.id + '?alt=media&key=' + API_KEY;
        } else if (/\.md$/i.test(f.name)) {
          mdMap[f.name.replace(/\.md$/i, '').trim()] = f.id;
        }
      });
      return {
        apps: apps.map(function (f) { return parseFile(f, API_KEY, iconMap, mdMap, platform); }),
        icons: iconMap, mds: mdMap
      };
    });
  }

  function siblingKey(n) {
    var k = String(n || '').trim();
    if (/\.md$/i.test(k)) k = k.slice(0, -3);
    var m = k.match(/\.(png|PNG|jpg|jpeg|webp)$/);
    if (m) k = k.slice(0, -m[0].length);
    return k.trim();
  }

  function parseManifestSize(v) {
    if (v == null) return 0;
    if (typeof v === 'number') return Math.floor(v);
    var m = String(v).trim().match(/^([\d.]+)\s*([KMGT]?B)?$/i);
    if (!m) return 0;
    var mult = { GB: 1073741824, MB: 1048576, KB: 1024 }[(m[2] || '').toUpperCase()] || 1;
    return Math.floor(parseFloat(m[1]) * mult);
  }

  function parseManifestEntry(o, iconMap, mdMap) {
    if (!o) return null;
    var pkg = String(o.packageName || '').trim() ||
      ('com.manifest.' + String(o.name || 'app').toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.+|\.+$/g, '') || 'com.manifest.app');
    var name = String(o.name || '').trim() || pkg;
    var icon = o.iconUrl || '';
    if (!icon && o.iconFileName) {
      var fileId = iconMap[siblingKey(o.iconFileName)];
      if (fileId) icon = API + '/' + fileId + '?alt=media&key=' + API_KEY;
    }
    var mdId = o.mdFileName ? (mdMap[siblingKey(o.mdFileName)] || null) : null;
    return {
      id: 'manifest_' + pkg,
      packageName: pkg, name: name,
      versionName: String(o.versionName || '').trim() || '1.0', versionCode: o.versionCode || 1,
      size: parseManifestSize(o.size), fileName: o.fileName || '', mdId: mdId,
      iconUrl: icon, downloadUrl: o.downloadUrl || '',
      inlineDesc: mdId ? '' : (o.description || ''),
      platform: 'Windows'
    };
  }

  function fetchManifest(folderId) {
    return driveGet({
      q: "'" + folderId + "' in parents and name = 'store_manifest.json' and trashed = false",
      key: API_KEY, fields: 'files(id)'
    }).then(function (d) {
      if (!d.files || !d.files[0]) return [];
      return fetch(API + '/' + d.files[0].id + '?alt=media&key=' + API_KEY).then(function (r) {
        if (!r.ok) throw new Error('store_manifest.json download failed (HTTP ' + r.status + ')');
        return r.json().then(function (j) {
          if (!Array.isArray(j)) throw new Error('store_manifest.json must contain a [...] array');
          return j;
        }).catch(function (e) {
          throw new Error('store_manifest.json invalid: ' + (e.message || 'bad JSON'));
        });
      });
    });
  }

  function fetchManifestSafe(folderId) {
    return fetchManifest(folderId).catch(function (e) {
      toast(e.message);
      return [];
    });
  }

  function dedupe(list) {
    var byPkg = {};
    list.forEach(function (a) {
      if (!byPkg[a.packageName] || a.id.length > byPkg[a.packageName].id.length) byPkg[a.packageName] = a;
    });
    return Object.keys(byPkg).map(function (k) { return byPkg[k]; });
  }

  function listFolderMaps(folderId) {
    return listAll("'" + folderId + "' in parents and trashed = false").then(function (files) {
      var icons = {}, mds = {};
      files.forEach(function (f) {
        if ((f.mimeType || '').indexOf('image/') === 0) {
          icons[f.name.replace(/\.(png|PNG|jpg|jpeg|webp)$/, '').trim()] = f.id;
        } else if (/\.md$/i.test(f.name)) {
          mds[f.name.replace(/\.md$/i, '').trim()] = f.id;
        }
      });
      return { icons: icons, mds: mds };
    }).catch(function () { return { icons: {}, mds: {} }; });
  }

  function loadApps() {
    var stats = $('statsLine'), list = $('appList'), empty = $('emptyState');
    stats.textContent = 'Loading apps...';
    list.innerHTML = '';
    empty.hidden = true;
    $('pullHint').hidden = false;

    var otherPlatform = state.platform === 'Android' ? 'Windows' : 'Android';
    findSubfolder(ROOT_FOLDER, state.platform).then(function (subId) {
      return findSubfolder(ROOT_FOLDER, otherPlatform).then(function (otherSubId) {
        return findSubfolder(ROOT_FOLDER, 'Web').then(function (webId) {
          var jobs = [parseFolder(ROOT_FOLDER, state.platform)];
          if (subId) jobs.push(parseFolder(subId, state.platform));
          jobs.push(otherSubId ? parseFolder(otherSubId, otherPlatform) : Promise.resolve(null));
          jobs.push(webId ? listFolderMaps(webId) : Promise.resolve(null));
          jobs.push(webId ? fetchManifest(webId) : Promise.resolve([]));
          return Promise.all(jobs);
        });
      });
    }).then(function (parts) {
      var manifest = parts[parts.length - 1] || [];
      var appFolders = parts.slice(0, parts.length - 3);
      var otherMaps = parts[parts.length - 3];
      var webMaps = parts[parts.length - 2];
      var iconMap = {}, mdMap = {}, all = [];
      function mergeMaps(f) {
        if (!f) return;
        Object.keys(f.icons).forEach(function (k) { if (!iconMap[k]) iconMap[k] = f.icons[k]; });
        Object.keys(f.mds).forEach(function (k) { if (!mdMap[k]) mdMap[k] = f.mds[k]; });
      }
      mergeMaps(webMaps);
      appFolders.forEach(function (f) { if (f) all = all.concat(f.apps); mergeMaps(f); });
      mergeMaps(otherMaps);
      manifest.forEach(function (m) {
        var app = parseManifestEntry(m, iconMap, mdMap);
        if (!app || app.platform !== state.platform) return;
        var idx = -1;
        for (var i = 0; i < all.length; i++) {
          if (all[i].packageName === app.packageName) { idx = i; break; }
        }
        if (idx >= 0) all[idx] = app; else all.push(app);
      });
      all = dedupe(all);
      all.sort(function (a, b) { return a.name.localeCompare(b.name); });
      state.apps = all;
      state.storeApp = all.find(function (a) { return a.packageName === 'com.mahmuduls.appstore'; }) || null;
      var vf = $('versionFooter');
      if (vf) vf.textContent = state.storeApp ? 'Version ' + state.storeApp.versionName : '';
      render();
    }).catch(function (e) {
      stats.textContent = 'Failed to load apps. Check connection and retry.';
      toast('Load failed: ' + e.message);
    }).finally(function () {
      $('pullHint').hidden = true;
    });
  }

  function render() {
    var q = state.query.trim().toLowerCase();
    var list = state.apps.filter(function (a) {
      return !q || a.name.toLowerCase().indexOf(q) !== -1 || a.packageName.toLowerCase().indexOf(q) !== -1;
    });
    $('statsLine').textContent = state.apps.length + ' apps • updated live from Google Drive';
    $('emptyState').hidden = list.length !== 0;
    $('appList').innerHTML = list.map(function (a, i) {
      return '<div class="app-card" data-i="' + i + '">' +
        (a.iconUrl ? '<img class="app-icon" loading="lazy" src="' + esc(a.iconUrl) + '" alt="">' :
          '<div class="app-icon" style="display:flex;align-items:center;justify-content:center;font-weight:700;font-size:22px;color:var(--primary)">' + esc(a.name.charAt(0).toUpperCase()) + '</div>') +
        '<div class="app-info"><div class="app-name">' + esc(a.name) + '</div>' +
        (a.platform === 'Windows' ? '' : '<div class="app-pkg">' + esc(a.packageName) + '</div>') +
        '<div class="app-ver">v' + esc(a.versionName) + (a.size ? ' • ' + esc(fmtSize(a.size)) : '') + '</div></div>' +
        '<a class="btn-download" href="' + esc(a.downloadUrl) + '" data-dl="' + i + '">Download</a>' +
        '</div>';
    }).join('');
    // stash filtered list for modal lookup
    render._list = list;
  }

  function fetchMd(mdId) {
    return fetch(API + '/' + mdId + '?alt=media&key=' + API_KEY).then(function (r) {
      if (!r.ok) throw new Error('md ' + r.status);
      return r.text();
    });
  }

  function inlineMd(s) {
    // escape, unescape md, bold, links (mirrors the Android app dialog)
    s = s.replace(/\\_/g, '_');
    s = esc(s);
    s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/(https?:\/\/[\w\d\-._~:/?#\[\]@!$&'()*+,;=%]+)/g, '<a href="$1" target="_blank" rel="noopener">$1</a>');
    return s;
  }

  function renderMd(text) {
    var html = '', para = [];
    function flushPara() {
      if (para.length) { html += '<p>' + para.join('<br>') + '</p>'; para = []; }
    }
    text.split('\n').forEach(function (rawLine) {
      var raw = rawLine.replace(/^\s+/, '');
      if (!raw.trim()) return;
      var h = raw.indexOf('### ') === 0 ? 3 : raw.indexOf('## ') === 0 ? 2 : raw.indexOf('# ') === 0 ? 1 : 0;
      var isNum = /^\d+\.\s/.test(raw);
      var isBul = raw.indexOf('* ') === 0 || raw.indexOf('- ') === 0;
      if (h > 0) {
        flushPara();
        html += '<div class="md-h' + h + '">' + inlineMd(raw.replace(/^#{1,3}\s+/, '')) + '</div>';
      } else if (isBul || isNum) {
        flushPara();
        var body = isNum ? raw.replace(/^\d+\.\s/, '') : raw.slice(2);
        var prefix = isNum ? raw.match(/^\d+/)[0] + '. ' : '• ';
        html += '<div class="md-li">' + esc(prefix) + inlineMd(body) + '</div>';
      } else {
        para.push(inlineMd(raw.trim()));
      }
    });
    flushPara();
    return html || '<p>No description.</p>';
  }

  function sectionAfter(text, keyword) {
    var lines = text.split('\n');
    for (var i = 0; i < lines.length; i++) {
      var t = lines[i].trim();
      if (t.indexOf('## ') === 0 && t.toLowerCase().indexOf(keyword) !== -1) {
        return lines.slice(i + 1).join('\n').trim();
      }
    }
    return '';
  }

  function firstUrl(text) {
    var m = text.match(/https?:\/\/[\w\d\-._~:/?#\[\]@!$&'()*+,;=%]+/);
    return m ? m[0] : null;
  }

  function openModal(app) {
    $('mIcon').src = app.iconUrl || 'icons/icon.png';
    $('mDownload').style.display = '';
    $('mDownload').textContent = 'Download';
    $('mDownload').href = app.downloadUrl;
    $('mName').textContent = app.name;
    $('mPkg').textContent = app.platform === 'Windows' ? '' : app.packageName;
    $('mPkg').style.display = app.platform === 'Windows' ? 'none' : '';
    $('mVer').textContent = 'v' + app.versionName + (app.size ? ' • ' + fmtSize(app.size) : '');
    $('mDesc').innerHTML = 'Loading description...';
    $('mActions').innerHTML = '';
    $('modal').hidden = false;
    document.body.style.overflow = 'hidden';
    function finishDesc(text) {
      var clean = (text || '').trim();
      $('mDesc').innerHTML = clean ? renderMd(clean) : '<p>No description.</p>';
      // Copy Settings / Open Link / Share App buttons, same as the app dialog
      var btns = '';
      var settings = sectionAfter(clean, 'settings');
      if (settings) btns += '<button class="btn-outline" data-act="copy">Copy Settings</button>';
      var linkUrl = firstUrl(sectionAfter(clean, 'link'));
      if (linkUrl) btns += '<a class="btn-outline" href="' + esc(linkUrl) + '" target="_blank" rel="noopener">Open Link</a>';
      $('mActions').innerHTML = btns;
      $('mActions').querySelectorAll('button').forEach(function (b) {
        b.addEventListener('click', function () {
          var t = settings.replace(/\\_/g, '_');
          if (navigator.clipboard) navigator.clipboard.writeText(t).then(function () { toast('Settings copied'); });
        });
      });
      $('mActions')._app = app;
      $('mActions')._settings = settings;
    }
    if (app.mdId) {
      fetchMd(app.mdId).then(finishDesc).catch(function () { finishDesc(''); });
    } else if (app.inlineDesc) {
      finishDesc(app.inlineDesc);
    } else {
      finishDesc('Discovered APK from Google Drive: ' + app.fileName + '.');
    }
  }

  function closeModal() {
    $('modal').hidden = true;
    document.body.style.overflow = '';
  }

  function findFileByName(folderId, name) {
    return driveGet({
      q: "'" + folderId + "' in parents and name = '" + name + "' and trashed = false",
      key: API_KEY,
      fields: 'files(id,name)'
    }).then(function (d) {
      return (d.files && d.files[0]) || null;
    });
  }

  function findFileByPrefix(folderId, prefix, suffix) {
    return listAll("'" + folderId + "' in parents and trashed = false").then(function (files) {
      return files.find(function (f) {
        return f.name.indexOf(prefix) === 0 && (!suffix || f.name.toLowerCase().endsWith(suffix));
      }) || null;
    });
  }

  function parseContactMd(text) {
    var entries = [];
    text.split('\n').forEach(function (raw) {
      var line = raw.trim().replace(/^[-*]\s+/, '');
      var i = line.indexOf(':');
      if (i > 0) {
        var label = line.slice(0, i).trim(), value = line.slice(i + 1).trim();
        if (label && value) entries.push({ label: label, value: value });
      }
    });
    return entries;
  }

  function linkifyContact(value) {
    var v = value;
    if (/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v)) return '<a href="mailto:' + esc(v) + '">' + esc(v) + '</a>';
    if (/^https?:\/\//i.test(v)) return '<a href="' + esc(v) + '" target="_blank" rel="noopener">' + esc(v) + '</a>';
    if (/^\+?[0-9][0-9\s\-()]{5,}$/.test(v)) {
      var num = v.replace(/[^0-9+]/g, '');
      return '<a href="https://wa.me/' + esc(num) + '" target="_blank" rel="noopener">' + esc(v) + '</a>';
    }
    return esc(v);
  }

  function openAbout() {
    $('mIcon').src = 'icons/icon.png';
    $('mDownload').style.display = '';
    $('mDownload').textContent = 'Get Android App';
    $('mDownload').href = 'https://github.com/mhasanbogura/appstore/releases';
    var s = state.storeApp;
    $('mName').textContent = 'App Store';
    $('mPkg').textContent = 'com.mahmuduls.appstore';
    $('mPkg').style.display = '';
    $('mVer').textContent = s ? 'v' + s.versionName : '';
    $('mDesc').textContent = 'Loading description...';
    $('modal').hidden = false;
    document.body.style.overflow = 'hidden';
    findFileByPrefix(ROOT_FOLDER, 'App Store_', '.md').then(function (f) {
      if (!f) throw new Error('no md');
      return fetchMd(f.id);
    }).then(function (t) { $('mDesc').textContent = (t || '').trim() || 'No description.'; })
      .catch(function () { $('mDesc').textContent = 'A modern app store that syncs apps from Google Drive.'; });
  }

  function openContact() {
    $('mIcon').src = 'icons/icon.png';
    $('mDownload').style.display = 'none';
    $('mName').textContent = 'Contact Developer';
    $('mPkg').textContent = '';
    $('mPkg').style.display = '';
    $('mVer').textContent = '';
    $('mDesc').innerHTML = 'Loading...';
    $('modal').hidden = false;
    document.body.style.overflow = 'hidden';
    findFileByName(ROOT_FOLDER, 'Contact.md').then(function (f) {
      if (!f) throw new Error('no contact');
      return fetchMd(f.id);
    }).then(function (t) {
      var entries = parseContactMd(t || '');
      var nameE = entries.find(function (e) { return e.label.toLowerCase() === 'name'; });
      var descE = entries.find(function (e) { return e.label.toLowerCase() === 'description'; });
      if (nameE) $('mName').textContent = nameE.value;
      var html = '';
      if (descE) html += '<p class="contact-desc">' + esc(descE.value) + '</p>';
      html += '<div class="contact-list">' + entries.filter(function (e) {
        return e.label.toLowerCase() !== 'name' && e.label.toLowerCase() !== 'description';
      }).map(function (e) {
        return '<div><div class="dim" style="font-size:12px">' + esc(e.label) + '</div><div>' + linkifyContact(e.value) + '</div></div>';
      }).join('') + '</div>';
      $('mDesc').innerHTML = html || 'No contact info.';
    }).catch(function () { $('mDesc').textContent = 'Contact info not available.'; });
  }

  function shareApp() {
    var text = 'Check out App Store — A modern app store that syncs apps from Google Drive. Browse, download, install, and manage app updates, all in one convenient place.\n\nDownload: https://u2l.ai/AppStore';
    if (navigator.share) {
      navigator.share({ title: 'App Store', text: text }).catch(function () {});
    } else if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(function () { toast('Share text copied'); }, function () { toast('Copy failed'); });
    } else {
      toast('Sharing not supported');
    }
  }
  function setTheme(t) {
    document.documentElement.setAttribute('data-theme', t);
    try { localStorage.setItem('appstore-theme', t); } catch (e) {}
    var dt = $('darkToggle');
    if (dt) dt.checked = (t === 'dark');
    document.querySelectorAll('#themeRow .theme-btn').forEach(function (b) {
      b.classList.toggle('active', b.getAttribute('data-theme') === t);
    });
  }

  var currentView = 'home';
  function show(view, push) {
    currentView = view;
    $('view-home').hidden = view !== 'home';
    $('view-settings').hidden = view !== 'settings';
    $('btnSettings').style.opacity = view === 'settings' ? '0.45' : '1';
    window.scrollTo(0, 0);
    if (push !== false) {
      var h = view === 'settings' ? '#/settings' : '#/';
      if (location.hash !== h) location.hash = h;
    }
  }
  window.addEventListener('hashchange', function () {
    show(location.hash === '#/settings' ? 'settings' : 'home', false);
  });

  // ---------- events ----------
  document.querySelectorAll('.chip').forEach(function (c) {
    c.addEventListener('click', function () {
      document.querySelectorAll('.chip').forEach(function (x) { x.classList.remove('active'); });
      c.classList.add('active');
      state.platform = c.getAttribute('data-platform');
      loadApps();
    });
  });

  var searchT = null;
  $('searchInput').addEventListener('input', function (e) {
    clearTimeout(searchT);
    searchT = setTimeout(function () { state.query = e.target.value; render(); }, 250);
  });

  $('appList').addEventListener('click', function (e) {
    if (e.target.closest('a')) return; // let Download link work
    var card = e.target.closest('.app-card');
    if (!card) return;
    var app = (render._list || [])[parseInt(card.getAttribute('data-i'), 10)];
    if (app) openModal(app);
  });

  $('modalClose').addEventListener('click', closeModal);
  $('modal').addEventListener('click', function (e) { if (e.target === $('modal')) closeModal(); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeModal(); });

  var btnRefresh = $('btnRefresh');
  btnRefresh.addEventListener('click', function () {
    btnRefresh.classList.add('spinning');
    loadApps();
    setTimeout(function () { btnRefresh.classList.remove('spinning'); }, 1200);
  });

  $('btnSettings').addEventListener('click', function () {
    show(currentView === 'settings' ? 'home' : 'settings');
  });
  $('btnBrand').addEventListener('click', function () { show('home'); });

  var darkToggle = $('darkToggle');
  if (darkToggle) {
    darkToggle.addEventListener('change', function () {
      setTheme(darkToggle.checked ? 'dark' : 'light');
    });
  }

  $('rowAbout').addEventListener('click', openAbout);
  $('rowContact').addEventListener('click', openContact);

  // ---------- init (no login), default Light ----------
  var savedTheme = 'light';
  try {
    var st = localStorage.getItem('appstore-theme');
    savedTheme = (st === 'dark') ? 'dark' : 'light';
  } catch (e) {}
  setTheme(savedTheme);
  show(location.hash === '#/settings' ? 'settings' : 'home', false);
  loadApps();
})();
