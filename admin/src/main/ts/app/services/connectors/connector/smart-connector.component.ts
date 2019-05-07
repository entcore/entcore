import { Component, OnInit, OnDestroy } from "@angular/core";
import { Subscription } from "rxjs";
import { CasType } from "./CasType";
import { ConnectorModel, Session, SessionModel, GroupModel, RoleModel } from "../../../core/store";
import { ServicesService } from "../../services.service";
import { ActivatedRoute, Params, Router } from "@angular/router";
import { ServicesStore } from "../../services.store";
import { SpinnerService, NotifyService } from "../../../core/services";
import { Location } from "@angular/common";
import { BundlesService } from "sijil";

import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/operator/toPromise';
import { HttpClient } from "@angular/common/http";

@Component({
    selector: 'smart-connector',
    template: `
        <div class="panel-header">
            <span *ngIf="isCreationMode() else isEditionMode">
                <s5l>services.connector.create.title</s5l>
            </span>
            <ng-template #isEditionMode>
                <span>{{ servicesStore.connector.displayName }}</span>
            </ng-template>
        </div>
    
        <div class="tabs" *ngIf="!isCreationMode()">
            <button class="tab"
                    [ngClass]="{active: currentTab === PROPERTIES_TAB}"
                    (click)="currentTab = PROPERTIES_TAB">
                {{ 'services.tab.properties' | translate }}
            </button>
            <button class="tab"
                    [ngClass]="{active: currentTab === ASSIGNMENT_TAB}"
                    (click)="currentTab = ASSIGNMENT_TAB">
                {{ 'services.tab.assignment' | translate }}
            </button>
        </div>

        <connector-properties
            *ngIf="currentTab === PROPERTIES_TAB"
            [connector]="servicesStore.connector"
            [casTypes]="casTypes"
            [structureChildren]="hasStructureChildren()"
            [creationMode]="isCreationMode()"
            [disabled]="arePropertiesDisabled()"
            (create)="onCreate($event)"
            (save)="onSave($event)">
        </connector-properties>

        <connector-assignment
            *ngIf="currentTab === ASSIGNMENT_TAB"
            [connector]="servicesStore.connector"
            (remove)="onRemoveAssignment($event)"
            (add)="onAddAssignment($event)">
        </connector-assignment>
    `,
    styles: [`
        button.tab {
            border-left: 0;
            box-shadow: none;
            border-right: 0;
            border-top: 0;
            margin: 0 10px;
            padding-left: 10px;
            padding-right: 10px;
        }
    `, `
        button.tab:hover {
            color: #ff8352;
            background-color: #fff;
            border-bottom-color: #ff8352;
        }
    `]
})
export class SmartConnectorComponent implements OnInit, OnDestroy {
    public casTypes: CasType[];
    private routeSubscription: Subscription;
    private rolesSubscription: Subscription;
    private casTypesSubscription: Subscription;
    public admc: boolean;
    
    public PROPERTIES_TAB = 'properties';
    public ASSIGNMENT_TAB = 'assignment';
    public MASS_ASSIGNEMENT_TAB = 'massAssignment';
    public currentTab: string = this.PROPERTIES_TAB;

    constructor(private servicesService: ServicesService,
                private activatedRoute: ActivatedRoute,
                public servicesStore: ServicesStore,
                private spinnerService: SpinnerService,
                private notifyService: NotifyService,
                private router: Router,
                private location: Location,
                private bundles: BundlesService,
                private httpClient: HttpClient) {
    }

    ngOnInit() {
        this.routeSubscription = this.activatedRoute.params.subscribe((params: Params) => {
            if (params['connectorId']) {
                this.servicesStore.connector = this.servicesStore.structure
                    .connectors.data.find(a => a.id === params['connectorId']);
            } else {
                this.servicesStore.connector = new ConnectorModel();
            }
        });

        this.rolesSubscription = this.activatedRoute.data.subscribe(data => {
            if(data["roles"]) {
                this.servicesStore.connector.roles = data["roles"];
                // Hack to gracful translate connector's role's name
                this.servicesStore.connector.roles.forEach(r => {
                    r.name = `${this.servicesStore.connector.name} - ${this.bundles.translate('services.connector.access')}`;
                });
            }
        })

        this.casTypesSubscription = this.servicesService
            .getCasTypes()
            .subscribe((res:CasType[]) => this.casTypes = res);

        this.setAdmc();
    }

    ngOnDestroy() {
        this.routeSubscription.unsubscribe();
        this.rolesSubscription.unsubscribe();
        this.casTypesSubscription.unsubscribe();
    }

    public async setAdmc() {
        const session: Session = await SessionModel.getSession();
        this.admc = session.functions && session.functions['SUPER_ADMIN'] != null;
    }

    public hasChildren(): boolean {
        return this.servicesStore.structure.children 
            &&  this.servicesStore.structure.children.length > 0;
    }

    public isCreationMode(): boolean {
        return this.servicesStore.connector && !this.servicesStore.connector.id;
    }

    public onCreate($event): void {
        if($event === 'submit') {
            this.spinnerService.perform('portal-content'
                , this.servicesService.createConnector(this.servicesStore.connector, this.servicesStore.structure.id)
                    .do(res => {
                        this.servicesStore.connector.id = res.id;
                        this.servicesStore.connector.structureId = this.servicesStore.structure.id;
                        this.servicesStore.structure.connectors.data.push(this.servicesStore.connector);
                        this.notifyService.success({
                            key: 'services.connector.create.success.content',
                            parameters: {connector: this.servicesStore.connector.displayName}
                        }, 'services.connector.create.success.title');
        
                        this.router.navigate(['..', res.id]
                            , {relativeTo: this.activatedRoute, replaceUrl: false});
                    })
                    .catch(error => {
                        if (error.error && error.error.error) {
                            this.notifyService.error(error.error.error
                                , 'services.connector.create.error.title'
                                , error);
                        } else {
                            this.notifyService.error({
                                key: 'services.connector.create.error.content'
                                , parameters: {connector: this.servicesStore.connector.displayName}
                            }, 'services.connector.create.error.title'
                            , error);
                        }
                        throw error;
                    })
                    .toPromise()
                );
        } else if ($event === 'cancel') {
            this.location.back();
        } else {
            console.error('unknown event from create EventEmitter');
        }
    }

    public onSave($event): void {
        let fieldsToSave = {};
        if ($event === 'parameters') {
            fieldsToSave = {
                name: this.servicesStore.connector.name,
                displayName: this.servicesStore.connector.displayName,
                icon: this.servicesStore.connector.icon || '',
                address: this.servicesStore.connector.url,
                target: this.servicesStore.connector.target || '',
                inherits: this.servicesStore.connector.inherits
            }
        } else if ($event === 'cas') {
            fieldsToSave = {
                appLocked: this.servicesStore.connector.locked,
                casType: this.servicesStore.connector.casTypeId || '',
                pattern: this.servicesStore.connector.casPattern || ''
            }
        } else if ($event === 'oauth') {
            fieldsToSave = {
                scope: this.servicesStore.connector.oauthScope || '',
                secret: this.servicesStore.connector.oauthSecret || '',
                grantType: this.servicesStore.connector.oauthGrantType || ''
            }
        } else {
            console.error('unknown event from save EventEmitter');
            return;
        }

        this.spinnerService.perform('portal-content'
            , this.servicesService.saveConnector(this.servicesStore.connector.id, this.servicesStore.structure.id, fieldsToSave)
                .do(res => {
                    this.notifyService.success({
                        key: 'services.connector.save.success.content',
                        parameters: {connector: this.servicesStore.connector.displayName}
                    }, 'services.connector.save.success.title');
                })
                .catch(error => {
                    if (error.error && error.error.error) {
                        this.notifyService.error(error.error.error
                            , 'services.connector.save.error.title'
                            , error);
                    } else {
                        this.notifyService.error({
                            key: 'services.connector.save.error.content'
                            , parameters: {connector: this.servicesStore.connector.displayName}
                        }, 'services.connector.save.error.title'
                        , error);
                    }
                    throw error;
                })
                .toPromise()
            );
    }

    public onAddAssignment($event: {group: GroupModel, role: RoleModel}) {
        $event.role.addGroup($event.group);
    }

    public onRemoveAssignment($event: {group: GroupModel, role: RoleModel}): void {
        $event.role.removeGroup($event.group);
    }
}