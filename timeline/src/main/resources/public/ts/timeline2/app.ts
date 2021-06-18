import angular = require("angular");
import { OdeModules } from 'ode-ngjs-front';
import { AppController } from "./controller";
import * as TimelineApp from './directives/timeline.directive';
import * as FlashMsg from './directives/flash-messages.directive';
import * as TimelineSettings from './directives/timeline-settings.directive';


angular.module("app", [OdeModules.getBase(), OdeModules.getI18n(), OdeModules.getUi(), OdeModules.getWidgets()])
.controller("appCtrl", ['$rootScope', '$scope', AppController])
.directive("timeline", TimelineApp.DirectiveFactory)
.directive("flashMessages", FlashMsg.DirectiveFactory)
.directive("timelineSettings", TimelineSettings.DirectiveFactory);
