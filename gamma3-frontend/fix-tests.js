const fs = require('fs');
const path = require('path');

function walkDir(dir, callback) {
  fs.readdirSync(dir).forEach(f => {
    let dirPath = path.join(dir, f);
    let isDirectory = fs.statSync(dirPath).isDirectory();
    isDirectory ? walkDir(dirPath, callback) : callback(path.join(dir, f));
  });
}

walkDir('c:/Projets/GAMMA3/gamma3-frontend/src/app', function(filePath) {
  if (filePath.endsWith('.spec.ts')) {
    let content = fs.readFileSync(filePath, 'utf-8');
    
    // Add imports if missing
    if (!content.includes('HttpClientTestingModule')) {
      content = "import { HttpClientTestingModule } from '@angular/common/http/testing';\nimport { RouterTestingModule } from '@angular/router/testing';\n" + content;
    }

    // Replace component imports
    content = content.replace(/imports:\s*\[(.*?)\]/, function(match, inner) {
      if (!inner.includes('HttpClientTestingModule')) {
        let newInner = inner.trim() ? inner + ', HttpClientTestingModule, RouterTestingModule' : 'HttpClientTestingModule, RouterTestingModule';
        return 'imports: [' + newInner + ']';
      }
      return match;
    });

    // Replace service configureTestingModule
    if (!content.includes('imports: [HttpClientTestingModule')) {
        content = content.replace(/configureTestingModule\(\{\}\)/, 'configureTestingModule({ imports: [HttpClientTestingModule, RouterTestingModule] })');
    }

    fs.writeFileSync(filePath, content, 'utf-8');
    console.log('Fixed ' + filePath);
  }
});
