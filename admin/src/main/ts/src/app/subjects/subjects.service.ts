import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import "rxjs/add/operator/do";
import {SubjectsStore} from "./subjects.store";
import {SubjectModel} from "../core/store/models/subject.model";

@Injectable()
export class SubjectsService {

    constructor(private httpClient: HttpClient,
                public subjectsStore: SubjectsStore) {
    }

    public delete(subject: SubjectModel): Observable<void> {
        return this.httpClient.delete<void>(`/directory/subject/${subject.id}`)
            .do(() => {
                this.subjectsStore.structure.subjects.data.splice(
                    this.subjectsStore.structure.subjects.data.findIndex(s => s.id === s.id)
                    , 1);
            });
    }

    public update(subject: { id: string, label: string, code: string }): Observable<void> {
        return this.httpClient.put<void>(`/directory/subject/${subject.id}`, {
            label: subject.label,
            code: subject.code
        })
            .do(() => {
                let sub: SubjectModel = this.subjectsStore.structure.subjects.data.find(s => s.id === s.id);
                if (sub) {
                    sub.label = subject.label;
                    sub.code = subject.code;
                }
                this.subjectsStore.subject.label = subject.label;
                this.subjectsStore.subject.code = subject.code;
            });
    }
}