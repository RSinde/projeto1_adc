const ui = {
    api: '/rest',

    toggle(id) {
        const contents = document.querySelectorAll('.op-content');
        contents.forEach(c => {
            if (c.id !== id) c.style.display = 'none';
        });
        const target = document.getElementById(id);
        if (target) {
            target.style.display = (target.style.display === 'block') ? 'none' : 'block';
        }
    },

    async request(path, inputData = {}) {
        const payload = {
            token: { tokenID: sessionStorage.getItem('tokenID') || "" },
            input: inputData
        };
        const res = await fetch(this.api + path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        return await res.json();
    },

    async doRegister() {
        const data = {
            username: document.getElementById('reg-email').value,
            password: document.getElementById('reg-pass').value,
            confirmation: document.getElementById('reg-conf').value,
            phone: document.getElementById('reg-phone').value,
            address: document.getElementById('reg-addr').value,
            role: document.getElementById('reg-role').value
        };
        const res = await this.request('/createaccount', data);
        alert(res.status === "success" ? "Conta criada!" : "Erro: " + res.data);
    }, // <-- Vírgula obrigatória

    async doLogin() {
        const data = {
            username: document.getElementById('login-email').value,
            password: document.getElementById('login-pass').value
        };
        const res = await this.request('/login', data);
        if(res.status === "success") {
            sessionStorage.setItem('tokenID', res.data.tokenID);
            sessionStorage.setItem('username', res.data.username);
            location.reload();
        } else alert("Falha: " + res.data);
    },

    async op3() {
        this.toggle('op3-content');
        const res = await this.request('/showusers', {});
        if(res.status !== "success") return alert(res.data);
        let html = "<table border='1'><tr><th>Email</th><th>Role</th></tr>";
        res.data.forEach(u => html += `<tr><td>${u.username}</td><td>${u.role}</td></tr>`);
        document.getElementById('op3-content').innerHTML = html + "</table>";
    },

    async doDelete() {
        const res = await this.request('/deleteaccount', { username: document.getElementById('del-username').value });
        alert(res.status === "success" ? "Utilizador eliminado!" : res.data);
    },

    async doModify() {
        const data = {
            username: document.getElementById('mod-username').value,
            attributes: {
                phone: document.getElementById('mod-phone').value,
                address: document.getElementById('mod-addr').value
            }
        };
        const res = await this.request('/modaccount', data);
        alert(res.status === "success" ? "Dados atualizados!" : res.data);
    },

    async op6() {
        this.toggle('op6-content');
        const res = await this.request('/showauthsessions', {});
        if(res.status !== "success") return alert(res.data);
        let html = "<table border='1'><tr><th>Token</th><th>User</th></tr>";
        res.data.sessions.forEach(s => html += `<tr><td>${s.tokenID}</td><td>${s.username}</td></tr>`);
        document.getElementById('op6-content').innerHTML = html + "</table>";
    },

    async doShowRole() {
        const res = await this.request('/showuserrole', { username: document.getElementById('role-query-user').value });
        if(res.status === "success") {
            document.getElementById('op7-result').innerHTML = `<strong>Role: ${res.data.role}</strong>`;
        } else alert(res.data);
    },

    async doChangeRole() {
        const data = {
            username: document.getElementById('chrole-username').value,
            newRole: document.getElementById('chrole-new').value
        };
        const res = await this.request('/changeuserrole', data);
        alert(res.status === "success" ? "Cargo atualizado!" : res.data);
    },

    async doChangePwd() {
        const data = {
            username: sessionStorage.getItem('username'),
            oldPassword: document.getElementById('chpw-old').value,
            newPassword: document.getElementById('chpw-new').value
        };
        const res = await this.request('/changeuserpwd', data);
        alert(res.status === "success" ? "Password alterada!" : res.data);
    },

    async doLogout() { // Unificado aqui
        let target = document.getElementById('logout-username').value;
        const myUser = sessionStorage.getItem('username');
        if (!target) target = myUser;

        const res = await this.request('/logout', { username: target });
        if (res.status === "success") {
            alert("Logout concluído para: " + target);
            if (target === myUser) {
                sessionStorage.clear();
                location.reload();
            }
        } else alert("Erro: " + res.data);
    },

    async extra() {
        this.toggle('extra-content');
        const res = await fetch(this.api + '/listallattributes').then(r => r.json());
        document.getElementById('extra-content').innerHTML = `<pre>${JSON.stringify(res.data, null, 2)}</pre>`;
    }
};

window.onload = () => {
    const user = sessionStorage.getItem('username');
    if(user) document.getElementById('user-status').innerText = "Autenticado como: " + user;
};