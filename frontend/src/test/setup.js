import '@testing-library/jest-dom';
import { configure } from '@testing-library/dom';

// findBy*/waitFor default to one second, which is fine for a small component and not fine for a
// large one rendering under a loaded suite. The work is real; only the patience was wrong.
configure({ asyncUtilTimeout: 5000 });
