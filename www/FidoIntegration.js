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
        SUCCESS_USER_CHOICE_REQUIRED: 0x1001, // payload: [{name,id}]
        FAILURE: 0x2000, // payload: exception
        SIGNAL_PROGRESS_UPDATE: 0x3001, // payload: float
        SIGNAL_DEVICE_DISCOVERED: 0x3002, // payload: null
        SIGNAL_DEVICE_LOST: 0x3003, // payload: null
    },
    getAssertion: function(clientData /*json string*/, userPin /*string*/, rpId /*url string*/, userId /*base64 string*/, onMessage /*callback (code, payload) -> void*/) {
        runExecCatching(onMessage, 'getAssertion', [clientData, userPin, rpId, userId]);
    },
    reset: function(onMessage /*callback (code, payload) -> void*/) {
        runExecCatching(onMessage, 'reset', []);
    }
};

module.exports = FidoIntegration;