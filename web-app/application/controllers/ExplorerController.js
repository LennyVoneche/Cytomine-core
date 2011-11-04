
var ExplorerController = Backbone.Router.extend({

       tabs : null,

       routes: {
          "tabs-image-:idProject-:idImage-:idAnnotation"   :   "browse",
          "close"   :   "close"
       },

       initialize: function() {
       },

       initTabs : function() { //SHOULD BE OUTSIDE OF THIS CONTROLLER
          //create tabs if not exist
          if (this.tabs == null) {
             this.tabs = new ExplorerTabs({
                    el:$("#explorer > .browser"),
                    container : window.app.view.components.explorer
                 }).render();
          }
       },
       browse : function (idProject, idImage, idAnnotation) {
          var self = this;
          this.initTabs();
          var createBrowseImageViewTab = function() {
             var browseImageViewInitOptions = {};
             if (idAnnotation != "") {
                browseImageViewInitOptions.goToAnnotation = {value : idAnnotation};
             }

             self.tabs.addBrowseImageView(idImage, browseImageViewInitOptions);
             /*self.tabs.showTab(idImage);*/
             self.tabs.triggerRoute = false;
             var tabs = $("#explorer > .browser").children(".tabs");
             tabs.tabs("select", "#tabs-image-"+window.app.status.currentProject+"-"+idImage+"-");
             self.tabs.triggerRoute = true;
             window.app.view.showComponent(self.tabs.container);
             self.showView();
          };

          if (window.app.status.currentProject == undefined) {//direct access -> create dashboard
             window.app.controllers.dashboard.dashboard(idProject, createBrowseImageViewTab);

             /*setTimeout(createBrowseImageViewTab, 0);*/
             return;
          }


          createBrowseImageViewTab();


       },
       closeAll : function () {
          if (this.tabs == null) return;

          this.tabs = null;
          $("#explorer > .browser").empty();
       },

       showView : function() {
          $("#explorer > .browser").show();
          $("#explorer > .noProject").hide();
          window.app.view.showComponent(window.app.view.components.explorer);
       }

    });