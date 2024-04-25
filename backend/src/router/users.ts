import express, { Router } from "express";
import { getRejectedRequest, getRequestPlayer, getUsersMasters } from "../handlers/user";

export const users : Router = express.Router();

users.get('/masters/:username', getUsersMasters())
users.get('/requests/player/:user_id', getRequestPlayer());
users.get('/request/rejected/:user_id/:request_id', getRejectedRequest())