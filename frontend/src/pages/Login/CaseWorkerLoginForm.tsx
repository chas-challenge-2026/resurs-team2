import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { useAuth } from "../../components/hooks/useAuth";
import { caseworkerLoginSchema } from "./loginSchemas";

export const CaseWorkerLoginForm = () => {
  const navigate = useNavigate();
  const { loginCaseWorker } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (
    event: React.SyntheticEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();
    setError(null);

    const result = caseworkerLoginSchema.safeParse({
      email,
      password,
    });

    if (!result.success) {
      setError(
        result.error.issues[0]?.message ?? "Kontrollera dina uppgifter.",
      );
      return;
    }

    setLoading(true);

    try {
      await loginCaseWorker(result.data);
      navigate("/backoffice");
    } catch (error: unknown) {
      if (error instanceof Error) {
        setError(error.message);
      } else {
        setError("Felaktig e-post eller lösenord.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="tab-pane active">
      {error && <div className="alert alert-danger">{error}</div>}

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="email">E-postadress</label>
          <input
            type="email"
            className="form-control"
            id="email"
            placeholder="karin@resurs.se"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="password">Lösenord</label>
          <input
            type="password"
            className="form-control"
            id="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={loading}
        >
          {loading ? "Loggar in..." : "Logga in"}
        </button>
      </form>
    </div>
  );
};