function Wizard() {}

Wizard.prototype.validate = function(callback) {
    http().postFile("/directory/wizard/validate", this.toFormData())
        .done(function(data) {
            if(typeof callback === 'function') {
                callback(data);
            }
        }).e400(function(e){
        var error = JSON.parse(e.responseText);
        if(typeof callback === 'function') {
            callback(error);
        } else {
            notify.error(error.error);
        }
    });
};

Wizard.prototype.import = function(fromMapping, profile, association, callback) {
    this.fromMapping = fromMapping;
    this.association = association;
    this.profile = profile;
    http().postFile("/directory/wizard/import", this.toFormData())
        .done(function(data) {
            if(typeof callback === 'function') {
                template.open('wizard-container', 'wizard-step2');
                callback(data);
            }
        }).e400(function(e){
        var error = JSON.parse(e.responseText);
        if(typeof callback === 'function') {
            callback(error);
        } else {
            notify.error(error.error);
        }
    });
}

Wizard.prototype.toFormData = function() {
    console.log(this);
    var formData = new FormData();
    for (var attr in this) {
        // TODO remove useless objects
        if (typeof this[attr] === 'function') continue;
        if (this[attr]) {
            if( attr == "association" ) {
                formData.append(attr, JSON.stringify(this[attr]));
            } else {
                formData.append(attr, this[attr]);
            }
        }
    }
    return formData;
};

Wizard.prototype.loadAvailableFeeders = function(callback){
    http().get('/directory/conf/public')
        .done(function(data) {
            if(typeof callback === 'function') {
                callback(data);
            }
        }).e400(function(e){
        var error = JSON.parse(e.responseText);
        if(typeof callback === 'function') {
            callback(error);
        } else {
            notify.error(error.error);
        }
    });
};

// preparing the mapping : get 2 lists :
//  - expected fields for the given profile
//  - heading of users file
Wizard.prototype.mapping = function(profile, fileName, callback) {
    http().get("/directory/wizard/mapping",
        {
            profile : profile,
            fileName : fileName
        })
        .done(function(data) {
            if(typeof callback === 'function') {
                callback(data);
            }
        }).e400(function(e){
        var error = JSON.parse(e.responseText);
        if(typeof callback === 'function') {
            callback(error);
        } else {
            notify.error(error.error);
        }
    });
};

Wizard.prototype.validateMapping = function(profile, association, fileName, callback) {
    association["profile"] = fileName;
    http().postJson("/directory/wizard/validateMapping/" + profile, association)
        .done(function(data) {
            if(typeof callback === 'function') {
                callback(data);
            }
        }).e400(function(e){
        var error = JSON.parse(e.responseText);
        if(typeof callback === 'function') {
            callback(error);
        } else {
            notify.error(error.error);
        }
    });
};

function Structure() {}

model.build = function() {
    this.makeModels([Structure]);
    this.collection(Structure, {
        sync: function(callback) {
            var that = this;
            http().get('structure/admin/list').done(function(structures) {
                that.load(structures);
                if(typeof callback === 'function') {
                    callback();
                }
            }).bind(this);
        }
    });
}
