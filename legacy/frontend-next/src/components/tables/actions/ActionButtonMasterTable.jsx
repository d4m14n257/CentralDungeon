import { useState, useRef } from 'react';

import IconButton from '@mui/material/IconButton';

import FeedbackIcon from '@mui/icons-material/Feedback';
import InfoIcon from '@mui/icons-material/Info';

import RequestUserModalTable from '../modals/RequestUserModalTable';
import UserModalInfo from '../../users/modal/UserModalInfo';

const user = {
    id: 1,
    name: 'Teshynil',
    discord: 'Teshynil#0001',
    joined_table: {
        inscrited_table: 10,
        active_table: 3,
        candidate_table: 4
    },
    master_table: {
        active_master_table: 3,
        created_master_table: 5,
    },
    comments: {
        positive_comments: 3,
        neutral_comments: 2,
        negative_comments: 7
    },
    karma: 1,
    country: 'Mexico',
    timezone: 'UTC-06:00'
}

export default function ActionButtonMasterTable (props) {
    const { id, status } = props;
    const user_id = useRef(null);
    const [request, setRequest] = useState(false);
    const [userInfo, setUserInfo] = useState(false);

    const handleOpenRequestUser = (id) => {
        user_id.current = id;
        setRequest(true);
    }

    const handleCloseRequestUser = () => {
        setRequest(false);
    }

    const handleOpenUserInfo = (id) => {
        user_id.current = id;
        setUserInfo(true);
    }

    const handleCloseUserInfo = () => {
        setUserInfo(false);
    }

    return (
        <>
            {status != 'Aceptado' &&
                <IconButton onClick={() => {handleOpenRequestUser(id)}} size="small">
                    <FeedbackIcon fontSize="inherit"/>
                </IconButton> 
            }
            <IconButton onClick={() => {handleOpenUserInfo(id)}} size="small">
                <InfoIcon fontSize="inherit"/>
            </IconButton>
            {request && 
                <RequestUserModalTable 
                    isOpen={request}
                    handleCloseModal={handleCloseRequestUser}
                    id={user_id.current}
                />
            }
            {userInfo && 
                <UserModalInfo 
                    isOpen={userInfo}
                    handleCloseModal={handleCloseUserInfo}
                    user={user}
                />
            }
        </>
    );
}