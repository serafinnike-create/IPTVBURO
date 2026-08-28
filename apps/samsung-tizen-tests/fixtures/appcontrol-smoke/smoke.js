/* Entrega um titulo sintetico ao app instalado; nunca usa fonte, stream ou credencial. */
'use strict';

(function () {
    var REPORT_BASE = 'http://10.0.2.2:43128/?result=';
    var PUBLIC_PROBE_URI = 'iptvburo://title?id=movie%3Auri-probe%3A2099&t=Teste%20de%20link&y=2099';
    var TARGET_APP_ID = 'IPTVBUROxx.IPTVBURO';

    function report(result) {
        var status = document.getElementById('status');
        if (status) { status.textContent = result; }
        try {
            var request = new XMLHttpRequest();
            request.open('GET', REPORT_BASE + encodeURIComponent(result) + '&ts=' + Date.now(), false);
            request.send(null);
        } catch (ignoredReport) {
            var beacon = new Image();
            beacon.src = REPORT_BASE + encodeURIComponent(result) + '&ts=' + Date.now();
        }
    }

    window.addEventListener('load', function () {
        var control;
        report('APPCONTROL_STAGE_STARTED');
        function launchResolvedTarget() {
            tizen.application.launchAppControl(control, TARGET_APP_ID, function () {
                report('APPCONTROL_LAUNCH_PASS');
            }, function (error) {
                report('APPCONTROL_LAUNCH_FAIL_' + String(error && error.name || 'UNKNOWN'));
            }, null);
        }
        try {
            control = new tizen.ApplicationControl(
                'http://tizen.org/appcontrol/operation/view',
                PUBLIC_PROBE_URI,
                null,
                null,
                null
            );
            tizen.application.findAppControl(control, function (applications) {
                var found = (applications || []).some(function (application) {
                    return application && application.id === TARGET_APP_ID;
                });
                if (!found) {
                    report('APPCONTROL_FILTER_NOT_FOUND');
                    return;
                }
                report('APPCONTROL_STAGE_FILTER_FOUND');
                launchResolvedTarget();
            }, function (error) {
                report('APPCONTROL_FIND_FAIL_' + String(error && error.name || 'UNKNOWN'));
            });
        } catch (error) {
            report('APPCONTROL_LAUNCH_FAIL_' + String(error && error.name || 'UNKNOWN'));
        }
    });
}());
