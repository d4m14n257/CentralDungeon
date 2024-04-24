import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';

import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';

export default function ViewMoreComponent ({ handleExpand, value }) {
    return (
        <Typography 
            sx={{textAlign: 'center'}}
        >
            <Button startIcon={<KeyboardArrowDownIcon />} onClick={() => {value ? handleExpand(value) : handleExpand()}}>
                Ver mas
            </Button>
        </Typography>
    );
}