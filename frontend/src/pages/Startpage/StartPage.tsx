import React from 'react';
import { useNavigate } from 'react-router-dom';
import './StartPage.css'; 

export const StartPage: React.FC = () => {
  const navigate = useNavigate();

  const handleStartClick = () => {
    navigate('/login');
  };

  return (
    <div className="hero-container">
      <div className="hero-content">
        <span className="hero-badge">Hej företagare!</span>
        <h1>Vi är inte som alla andra banker</h1>
        <p>Inte för dyr eller krånglig.</p>
        
        <ul className="hero-features">
          <li>✓ Finansiering upp till 300 miljoner kronor</li>
          <li>✓ Spara tryggt med ränta</li>
          <li>✓ Vi tittar på ditt ärende med andra ögon</li>
        </ul>

        <button className="hero-button" onClick={handleStartClick}>
          Kom igång med företagskredit
        </button>
      </div>
    </div>
  );
};

export default StartPage;