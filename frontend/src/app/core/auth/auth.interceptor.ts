import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AuthStateService } from './auth-state.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthStateService);
  const authorization = auth.authorizationHeader();
  const shouldAttachAuthorization = authorization !== null
    && request.url.startsWith(environment.apiBaseUrl)
    && !request.headers.has('Authorization');

  if (!shouldAttachAuthorization) {
    return next(request);
  }

  return next(request.clone({
    setHeaders: {
      Authorization: authorization
    }
  }));
};
