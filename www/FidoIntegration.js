const exec = require('cordova/exec');

function extractMessage(value) {
    if (value instanceof Error) {
        return value.message || value.toString();
    }
    if (typeof DOMException !== "undefined" && value instanceof DOMException) {
        return value.message || value.name;
    }
    if (typeof value === "string") {
        return value;
    }
    try {
        return JSON.stringify(value);
    } catch {
        return String(value);
    }
}

function runExecCatching(onMessage, action, parameters) {
    try {
        exec((result) => {
            onMessage(result.statusCode, result.payload);
        }, (_) => {}, 'FidoIntegration', action, parameters);
    } catch(err) {
        onMessage(0x2000, extractMessage(err));
    }
}

const FidoIntegration = {
    StatusCodes: {
        SUCCESS: 0x1000, // payload: result
        FAILURE: 0x2000, // payload: string (exception) | null
        FAILURE_INVALID_PIN: 0x2001, // payload: null
        FAILURE_DEVICE_UNSUPPORTED: 0x2002, // payload: null
        FAILURE_DEVICE_LOST: 0x2003, // payload: null
        FAILURE_NO_CREDENTIALS: 0x2004, // payload: null
        FAILURE_TOO_MANY_CREDENTIALS: 0x2005, // payload: [{name :string, id: string}]
        SIGNAL_PROGRESS_UPDATE: 0x3001, // payload: float
        SIGNAL_DEVICE_DISCOVERED: 0x3002 // payload: null
    },
    getAssertion: function(
        clientData /*string (json)*/,
        rpId /*string (url)*/,
        userPin /*string | null*/,
        userId /*string (base64) | null*/,
        onMessage /*callback (code, payload) -> void*/
    ) {
        runExecCatching(onMessage, 'getAssertion', [clientData, rpId, userPin, userId]);
    },
    nfcDevNull: function(onMessage /*callback (code, payload) -> void*/) {
        runExecCatching(onMessage, 'nfcDevNull', []);
    },
    reset: function(onMessage /*callback (code, payload) -> void*/) {
        runExecCatching(onMessage, 'reset', []);
    }
};

module.exports = FidoIntegration;