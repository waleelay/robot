import Vue from 'vue'
import Vuex from 'vuex'
import app from './modules/app'
import dict from './modules/dict'
import user from './modules/user'
import tagsView from './modules/tagsView'
import permission from './modules/permission'
import settings from './modules/settings'
import getters from './getters'
import websocket from "./modules/websocket";
import extra from "./modules/extra";
import voiceCall from "./modules/voiceCall";
import bigScreen from "./modules/bigScreen";
import alert from './modules/alert';
import dragVideo from './modules/dragVideo';
import websocketRobot from './modules/websocket-robot';
import websocketExtraData from './modules/websocket-extra-data';

Vue.use(Vuex)

const store = new Vuex.Store({
  modules: {
    app,
    dict,
    user,
    tagsView,
    permission,
    settings,
    websocket,
    extra,
    voiceCall,
    bigScreen,
    alert,
    dragVideo,
    websocketRobot,
    websocketExtraData,
  },
  getters
})

export default store
