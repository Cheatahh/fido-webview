const exec = require('cordova/exec');

const FidoWebview = {
    StatusCodes: {
        SUCCESS: 1, // payload: result
        FAILURE: 2, // payload: exception
        PROGRESS: 3  // payload: progress
    },
    getAssertion: function(clientData, userPin, onStatusChanged) {
        try {
            exec((result) => {
                onStatusChanged(result.statusCode, result.payload);
            }, (err) => {
                onStatusChanged(this.StatusCodes.FAILURE, err);
            }, 'FidoWebview', 'getAssertion', [clientData, userPin]);
        } catch(err) {
            onStatusChanged(this.StatusCodes.FAILURE, err);
        }
    },
    log: function(message) {
        exec(() => {}, () => {}, 'FidoWebview', 'log', [message]);
    }
};

module.exports = FidoWebview;