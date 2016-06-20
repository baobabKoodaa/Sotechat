/** Controlelri huolehtii ammattilaisen näkymän välilehtien hallinnasta
 * Seka ilmoittaa "lapsilleen" yhteyden muodostumisesta serveriin
 */
angular.module('chatProApp')
    .controller('proCPController', ['$scope','$timeout','$http', '$uibModal', 'connectToServer', 'proStateService', 'heartBeatService',
        function ($scope, $timeout, $http, $uibModal, connectToServer, proStateService) {
            /** Alustetaan muuttuja */
            var tabCount = 0;
            $scope.pro = true;
            $scope.chats = [];
            $scope.activeChatTab=tabCount+1;
            $scope.proView = 'proControlPanel/userHandlingArea.tpl.html';
            
            $scope.showHistory = function() {
                $scope.proView = 'proHistories/chatHistory.html';
            };

            $scope.backToPanel = function() {
                $scope.proView = 'proControlPanel/userHandlingArea.tpl.html';
            };

            /** Ilmoitetaan jono controllerille että yhteys serveriin on muodostetu */
            var initQueue = function () {
                $scope.$broadcast('connectedToQueue');
            };

            /** Lisää uuden chat välilehdin annetulla kanavaID:nä */
            $scope.addChatTab = function (channelID) {
                $scope.chats.push({title: 'Chat' + tabCount++, channel: channelID});
                $timeout(function(){
                    $scope.activeChatTab=tabCount-1;
                    console.log($scope.activeChatTab);
                });
            };
            
            $scope.removeChatTab = function(channelID) {
                var modalInstance = $uibModal.open({
                    animation: true,
                    templateUrl: 'common/areUSureModal.tpl.html',
                    controller: 'AreUSureModalController'
                });

                modalInstance.result.then(function(result) {
                    $scope.$broadcast('unSubscribeChat', {'channelID' : channelID});
                    $http.post("/leave/" + channelID, {});
                    console.log(tabCount);
                    tabCount--;
                    $timeout(function(){
                        $scope.activeChatTab=tabCount-1;
                    });
                    console.log(tabCount);
                    console.log($scope.activeChatTab);
                    console.log($scope.chats);
                    var chatTabIndex =  arrayObjectIndexOf($scope.chats, channelID, 'channel');
                    if (chatTabIndex > -1) {
                        $scope.chats.splice(chatTabIndex, 1);
                    }
                    console.log($scope.chats);
                });
            };

            var arrayObjectIndexOf = function(myArray, searchTerm, property) {
                for(var i = 0, len = myArray.length; i < len; i++) {
                    if (myArray[i][property] === searchTerm) return i;
                }
                return -1;
            }


            /** Avaa kaikki amamttilaisen avoimet välilehdet */
            var updateChannels = function () {
                tabCount = 1;
                $scope.chats = [];
                angular.forEach(proStateService.getChannelIDs(), function (key) {
                    $scope.chats.push({title: 'Chat' + tabCount, channel: key});
                    tabCount++;
                });
            };
            
            /** Kun yhteys serveriin on muodostettu alustetaan siitä riippuvat */
            var answer = function () {
                initQueue();
                $scope.username = proStateService.getUsername();
                updateChannels();
            };
            
            /** Päivittää ammattilaisen tiedot serveriltä
             *  Ja aloittaa alustuksen haun valmistuttua 
             */
            $scope.updateProStatus = function() {
                proStateService.getVariablesFormServer().then(function (response) {
                    proStateService.setAllVariables(response);
                    connectToServer.connect(answer);
                });
            };
            
            /** Pyytää alustusta kun kontrolleri ladataan */
            $scope.updateProStatus();
        }]);