const exec = require('cordova/exec');

function runExecCatching(onMessage, action, parameters) {
    try {
        exec((result) => {
            onMessage(result.statusCode, result.payload);
        }, (err) => {
            onMessage(this.StatusCodes.FAILURE, err);
        }, 'FidoIntegration', action, parameters);
    } catch(err) {
        onMessage(this.StatusCodes.FAILURE, err);
    }
}

const FidoIntegration = {
    StatusCodes: {
        SUCCESS: 0x1000, // payload: result
        FAILURE: 0x2000, // payload: exception
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
    reset: function(onMessage /*callback (code, payload) -> void*/) {
        runExecCatching(onMessage, 'reset', []);
    }
};

module.exports = FidoIntegration;