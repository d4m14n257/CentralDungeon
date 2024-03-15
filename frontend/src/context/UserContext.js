import { createContext } from "react";

export const UserContext = createContext({username: '', rol: '', roles: [], handleUpdateRol: () => {}});