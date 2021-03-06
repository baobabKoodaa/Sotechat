/**
 * Palvelu huolehtii kayttajan tilan yllapitamisesta
 * kontrolleri vaihdosten yli.
 */
angular.module('chatApp')
    .factory('userStateService', ['$http', function ($http) {
        var channelID;
        var username;
        var userID;
        var userState;

        /**
         * etterit ja setterit
         */
        function setChannelID(value) {
            channelID = value;
        }

        function setUsername(value) {
            username = value;
        }

        function setUserState(value) {
            userState = value;
        }

        function setUserID(value) {
            userID = value;
        }

        function getChannelID() {
            return channelID;
        }

        function getUsername() {
            return username;
        }

        function getUserID() {
            return userID;
        }

        function getState() {
            return userState;
        }

        /**
         * Palauttaa käyttäjän tilaavastaavan templaten osoitteen.
         */
        function getUserState() {
            if (userState === 'queue') {
                return 'queue/userInQueue.tpl.html'
            } else if (userState === 'chat') {
                return 'chatWindow/userInChat.tpl.html'
            } else if (userState === 'pro') {
                return 'staticErrorPages/sameBrowserError.html'
            } else if (userState === 'closed') {
                return 'common/chatClosed.tpl.html'
            } else {
                return 'queue/userToQueue.tpl.html';
            }
        }
        
        /**
         * Lahettaa poistumis ilmoituksen serverille.
         */
        function leaveChat() {
            $http.post("/leave/" + getChannelID(), {});
        }

        /**
         * Hakee get-pyynnolla palvelimelta kayttajan tiedot.
         */
        function getVariablesFormServer() {
            return $http.get("/userState");
        }

        /**
         * Asettaa vastauksessa tulleet parametrit palveluun.
         */
        function setAllVariables(response) {
            setUsername(response.data.username);
            setChannelID(response.data.channelId);
            setUserID(response.data.userId);
            setUserState(response.data.state);
        }

        var queue = {
            getVariablesFormServer: getVariablesFormServer,
            setAllVariables: setAllVariables,
            setUserState: setUserState,
            getUserState: getUserState,
            getChannelID: getChannelID,
            getUsername: getUsername,
            getUserID: getUserID,
            leaveChat: leaveChat,
            getState: getState
        };

        return queue;
    }]);
