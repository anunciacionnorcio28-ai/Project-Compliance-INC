import { Component } from '@angular/core';
import { SecurityGuideComponent } from '../shared/security-guide.component';

@Component({
  selector: 'app-security-page',
  imports: [SecurityGuideComponent],
  template: `
    <div class="col">
      <div class="page-head"><h1>Security guide</h1></div>
      <app-security-guide />
    </div>
  `,
})
export class SecurityPageComponent {}
