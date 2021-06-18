import { ng, http, $ } from 'entcore';
import { Subject, Observable } from "rxjs";
import { debounceTime } from "rxjs/operators";
import moment = require('moment');

interface ScopePdfViewer {
	pageIndex: number | any;
	numPages: number;
	$parent: {
		render: any
	}
	loading: boolean;
	nextPage(): void;
	openPage(): void;
	previousPage(): void;
	scroller: string;
	$apply: any;
}
let _loadedPdfJs = false;
function loadPdfJs():Promise<void>{
	if(_loadedPdfJs) return Promise.resolve();
	(window as any).PDFJS = { workerSrc: '/infra/public/js/viewers/pdf.js/pdf.worker.js' };
	return http().loadScript('/infra/public/js/viewers/pdf.js/pdf.js').then(e=>{
		_loadedPdfJs = true;
		return e;
	})
}
export const pdfViewer = ng.directive('pdfViewer', function () {
	return {
		restrict: 'E',
		template: `
			<div class="pagination__area flex-row align-center justify-center">
				<div class="file-controls left" ng-click="previousPage()"><i class="left"></i></div>
				<div class="pagination">
					<input type="text" ng-model="pageIndex" ng-change="openPage()" /> / [[numPages]]
				</div>
				<div class="file-controls right" ng-click="nextPage()"><i class="right"></i></div>
			</div>
			<p ng-if="loading" class="top-spacing-four flex-row align-start justify-center centered-text"><i18n>workspace.preview.loading</i18n>&nbsp;<i class="loading"></i></p>
			`,
		scope: {
			scroller: '=?',
		},
		link: function (scope: ScopePdfViewer, element, attributes) {
			let pdf:any;
			scope.loading = true;
			scope.pageIndex = 1;
			scope.nextPage = function () {
				if (scope.pageIndex < scope.numPages) {
					scope.pageIndex++;
					scope.openPage();
				}
			};
			scope.previousPage = function () {
				if (scope.pageIndex > 1) {
					scope.pageIndex--;
					scope.openPage();
				}
			};

			let keyNav = function(e)
			{
				switch(e.keyCode)
				{
					case 37: scope.previousPage(); break;
					case 39: scope.nextPage(); break;
				}
				scope.$apply();
			};
			document.addEventListener("keydown", keyNav);
			(scope as any).$on("$destroy", function()
			{
				document.removeEventListener("keydown", keyNav);
			});

			const reloadPdf = new Subject<number>();
			(reloadPdf as Observable<number>).pipe( debounceTime(100) ).subscribe(pageNumber=>{
				pdf.getPage(pageNumber).then(function (page) {
					var viewport;
					if (!$(canvas).hasClass('fullscreen')) {
						viewport = page.getViewport(1);
						var scale = element.width() / viewport.width;
						viewport = page.getViewport(scale);
					}
					else {
						viewport = page.getViewport(2);
					}

					var context = canvas.getContext('2d');
					canvas.height = viewport.height;
					canvas.width = viewport.width;

					var renderContext = {
						canvasContext: context,
						viewport: viewport
					};
					page.render(renderContext);
				});
			})
			scope.openPage = function () {
				var pageNumber = parseInt(scope.pageIndex);
				if (!pageNumber) {
					return;
				}
				if (pageNumber < 1) {
					pageNumber = 1;
				}
				if (pageNumber > scope.numPages) {
					pageNumber = scope.numPages;
				}
				reloadPdf.next(pageNumber)
			};
			scope.$parent.render = scope.openPage;

			var canvas = document.createElement('canvas');
			$(canvas).addClass('render');
			element.append(canvas);
			loadPdfJs().then(() => {
				(window as any).PDFJS
					.getDocument(attributes.ngSrc)
					.then(function (file) {
						pdf = file;
						scope.numPages = pdf.pdfInfo.numPages;
						scope.$apply('numPages');
						scope.openPage();
						scope.loading = false;
						scope.$apply('loading');
					}).catch(function(e){
						scope.loading = false;
						scope.$apply('loading');
					})
			});

			if(scope.scroller != null && scope.scroller != "")
			{
				let scrollerEl = document.querySelector(scope.scroller);
				let lastTick = moment(0).valueOf();
				if(scrollerEl != null)
				{
					scrollerEl.addEventListener("wheel", function(e: WheelEvent)
					{
						if(moment().valueOf() - lastTick <= 250)
							return;

						var pageNumber = parseInt(scope.pageIndex);
						if(e.deltaY > 0 && scrollerEl.scrollHeight - scrollerEl.scrollTop === scrollerEl.clientHeight && pageNumber < scope.numPages)
						{
							scope.nextPage();
							lastTick = moment().valueOf();
							scrollerEl.scrollTop = 0;
							e.preventDefault();
							scope.$apply();
						}
						else if(e.deltaY < 0 && scrollerEl.scrollTop == 0 && pageNumber > 1)
						{
							scope.previousPage();
							lastTick = moment().valueOf();
							scrollerEl.scrollTop = scrollerEl.scrollHeight - scrollerEl.clientHeight;
							e.preventDefault();
							scope.$apply();
						}
					});
				}
			}
		}
	}
});
