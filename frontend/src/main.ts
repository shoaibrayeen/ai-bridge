import { bootstrapApplication } from '@angular/platform-browser';
import { provideZoneChangeDetection } from '@angular/core';
import { AppComponent } from './app/app.component';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app/app.routes';
import { authInterceptor } from './app/shared/interceptors/auth.interceptor';

bootstrapApplication(AppComponent, {
  providers: [
    // Angular 21 defaults to zoneless change detection. This app's components mutate plain
    // fields inside subscribe() callbacks, which zoneless never notices — every page that
    // loads data over HTTP froze at its first render ("Loading…" forever) while the requests
    // succeeded underneath. zone.js is already in the polyfills; opt back into zone-based CD
    // until the components are migrated to signals.
    provideZoneChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimations()
  ]
});
