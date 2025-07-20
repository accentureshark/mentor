import { BrowserRouter as Router } from 'react-router-dom';
import McpClientApp from './pages/McpClientApp';

import 'primereact/resources/themes/lara-light-indigo/theme.css';
import 'primereact/resources/primereact.min.css';
import 'primeicons/primeicons.css';

import './App.css';

function App() {
  return (
    <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <div className="App">
        <McpClientApp />
      </div>
    </Router>
  );
}

export default App;