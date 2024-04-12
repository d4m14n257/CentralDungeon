import express, { Router } from "express";
import { getUsersMasters } from "../handlers/user";

export const users : Router = express.Router();

users.get('/masters/:username', getUsersMasters())