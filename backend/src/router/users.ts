import express, { Router } from "express";
import { getRequestPlayer, getUsersMasters } from "../handlers/user";

export const users : Router = express.Router();

users.get('/masters/:username', getUsersMasters())
users.get('/requests/player/:user_id', getRequestPlayer());