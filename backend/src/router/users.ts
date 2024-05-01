import express, { Router } from "express";
import { 
        handleGetRejectedRequest, 
        handleGetRequestPlayer, 
        handleGetUsersMasters 
    } from "../handlers/user";

export const users : Router = express.Router();

users.get('/masters/:username', handleGetUsersMasters())
users.get('/requests/player/:user_id', handleGetRequestPlayer());
users.get('/request/rejected/:user_id/:request_id', handleGetRejectedRequest())