import { useState } from 'react';

import Card from '@mui/material/Card';
import CardHeader from '@mui/material/CardHeader';
import IconButton from '@mui/material/IconButton';
import Box from '@mui/material/Box';
import Menu from '@mui/material/Menu';

import InfoIcon from '@mui/icons-material/Info';

import MenuItemComponent from '../MenuItemComponent';
import { global } from '@/styles/global';
import Typography from '@mui/material/Typography'

export default function CardComponent (props) {
    const { title, subtitle, additional, menu, children } = props;
    const [openMenu, setOpenMenu] = useState(null);
    const open = Boolean(openMenu);

    const handleOpenMenu = (event) => {
        setOpenMenu(event.currentTarget)
    }

    const handleCloseMenu = () => {
        setOpenMenu(null);
    };

    return (
        <>
            <Card sx={{...global.border, padding: 2}}>
                <Box sx={additional ? {...global.rowFlex, alignItems: "center", justifyContent: 'space-between'} : {}}>
                    <CardHeader
                        title={title}
                        subheader={subtitle}
                        action={menu &&
                            <IconButton
                                sx={{ ml: 2 }}
                                onClick={handleOpenMenu}
                            >
                                <InfoIcon />
                            </IconButton>
                        }
                    />
                    {additional && 
                        <Typography variant="subtitle1" sx={{paddingRight: 2}}>{additional}</Typography>
                    }  
                </Box>
                {children}
            </Card>
            {menu &&
                <Menu 
                    anchorEl={openMenu}
                    id="account-menu"
                    open={open}
                    onClose={handleCloseMenu}
                    onClick={handleCloseMenu}
                    transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                    anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                >
                    {menu.map((item) => (
                        <MenuItemComponent
                            id={item.name}
                            name={item.name}
                            Icon={item.Icon}
                            handleClickMenu={item.handleClickMenu}
                        />
                    ))}
                </Menu>
            }
        </>
    );
}